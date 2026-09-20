package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
  }

  /**
   * The store keeps the write-side type (PostPaymentResponse); the read contract is a separate
   * type, so the service translates between them here rather than coupling the two DTOs.
   */
  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    PostPaymentResponse stored = paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));

    GetPaymentResponse payment = new GetPaymentResponse();
    payment.setId(stored.getId());
    payment.setStatus(stored.getStatus());
    payment.setCardNumberLastFour(stored.getCardNumberLastFour());
    payment.setExpiryMonth(stored.getExpiryMonth());
    payment.setExpiryYear(stored.getExpiryYear());
    payment.setCurrency(stored.getCurrency());
    payment.setAmount(stored.getAmount());
    return payment;
  }

  /**
   * Orchestrates a payment, stored the result into Repository.
   */
  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    BankPaymentResponse bankResponse = bankClient.processPayment(paymentRequest);

    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setStatus(bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    payment.setCardNumberLastFour(paymentRequest.getCardNumberLastFour());
    payment.setExpiryMonth(paymentRequest.getExpiryMonth());
    payment.setExpiryYear(paymentRequest.getExpiryYear());
    payment.setCurrency(paymentRequest.getCurrency());
    payment.setAmount(paymentRequest.getAmount());

    paymentsRepository.add(payment);
    LOG.info("Processed payment {} with status {}", payment.getId(), payment.getStatus());
    return payment;
  }
}
