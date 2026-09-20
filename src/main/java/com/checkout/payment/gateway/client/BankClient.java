package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Gateway to the acquiring bank simulator
 */
@Component
public class BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    BankPaymentRequest bankRequest = BankPaymentRequest.from(paymentRequest);
    LOG.debug("Forwarding payment to acquiring bank: {}", bankRequest);
    try {
      return restTemplate.postForEntity(bankUrl, bankRequest, BankPaymentResponse.class).getBody();
    } catch (RestClientException e) {
      throw new BankCommunicationException("Acquiring bank is unavailable", e);
    }
  }
}
