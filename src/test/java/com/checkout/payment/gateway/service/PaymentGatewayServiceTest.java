package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Service orchestration with the bank client and repository mocked: verifies the authorized /
 * declined mapping and that a stored payment carries only the masked card fragment.
 */
@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;
  @Mock
  private BankClient bankClient;
  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  private PostPaymentRequest request() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343240042");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(1050);
    request.setCvv("123");
    return request;
  }

  private BankPaymentResponse bankDecision(boolean authorized) {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(authorized);
    response.setAuthorizationCode(authorized ? "some-code" : "");
    return response;
  }

  @Test
  void authorizedBankDecisionProducesAuthorizedPayment() {
    when(bankClient.processPayment(any())).thenReturn(bankDecision(true));

    PostPaymentResponse payment = paymentGatewayService.processPayment(request());

    assertEquals(PaymentStatus.AUTHORIZED, payment.getStatus());
  }

  @Test
  void declinedBankDecisionProducesDeclinedPaymentAndIsStillStored() {
    when(bankClient.processPayment(any())).thenReturn(bankDecision(false));

    PostPaymentResponse payment = paymentGatewayService.processPayment(request());

    assertEquals(PaymentStatus.DECLINED, payment.getStatus());
    verify(paymentsRepository).add(payment);
  }

  @Test
  void storedPaymentCarriesMaskedCardDetailsOnly() {
    when(bankClient.processPayment(any())).thenReturn(bankDecision(true));

    paymentGatewayService.processPayment(request());

    ArgumentCaptor<PostPaymentResponse> stored = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository).add(stored.capture());
    // String last-four preserves leading zeros: a card ending 0042 must stay "0042".
    assertEquals("0042", stored.getValue().getCardNumberLastFour());
    assertEquals(4, stored.getValue().getExpiryMonth());
    assertEquals(2027, stored.getValue().getExpiryYear());
    assertEquals("GBP", stored.getValue().getCurrency());
    assertEquals(1050, stored.getValue().getAmount());
  }

  @Test
  void bankFailurePropagatesAndNothingIsStored() {
    when(bankClient.processPayment(any()))
        .thenThrow(new BankCommunicationException("Acquiring bank is unavailable", null));

    assertThrows(BankCommunicationException.class, () -> paymentGatewayService.processPayment(request()));
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void unknownPaymentIdThrows() {
    when(paymentsRepository.get(any())).thenReturn(Optional.empty());

    assertThrows(EventProcessingException.class, () -> paymentGatewayService.getPaymentById(UUID.randomUUID()));
  }
}
