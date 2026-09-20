package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * BankClient against a MockRestServiceServer, so the three simulator behaviors (authorized,
 * declined, 503) plus a transport failure are tested without Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class BankClientTest {

  private static final String BANK_URL = "http://localhost:8080/payments";

  private MockRestServiceServer server;
  private BankClient bankClient;

  @BeforeEach
  void setUp() {
    // Spring Boot's logging system is JVM-global and reset by other tests' contexts; pinning
    // the level here keeps the log-capture assertions independent of test execution order.
    ((Logger) LoggerFactory.getLogger(BankClient.class)).setLevel(Level.DEBUG);
    RestTemplate restTemplate = new RestTemplateBuilder().build();
    server = MockRestServiceServer.createServer(restTemplate);
    bankClient = new BankClient(restTemplate, BANK_URL);
  }

  private PostPaymentRequest request() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(1050);
    request.setCvv("123");
    return request;
  }

  @Test
  void authorizedResponseIsParsedFromTheFlagNotTheHttpStatus() {
    server.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        // The wire format maps expiry month/year to the simulator's MM/YYYY string.
        .andExpect(jsonPath("$.expiry_date").value("04/2027"))
        .andExpect(jsonPath("$.card_number").value("2222405343248877"))
        .andRespond(withSuccess("{\"authorized\": true, \"authorization_code\": \"auth-123\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentResponse response = bankClient.processPayment(request());

    assertTrue(response.isAuthorized());
    assertEquals("auth-123", response.getAuthorizationCode());
    server.verify();
  }

  @Test
  void declinedResponseIsA200WithAuthorizedFalse() {
    server.expect(requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\": false, \"authorization_code\": \"\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentResponse response = bankClient.processPayment(request());

    assertFalse(response.isAuthorized());
    server.verify();
  }

  @Test
  void bankServerErrorBecomesBankCommunicationException() {
    server.expect(requestTo(BANK_URL)).andRespond(withServerError());

    assertThrows(BankCommunicationException.class, () -> bankClient.processPayment(request()));
  }

  @Test
  void bankRequestIsLoggedWithPanAndCvvMasked(CapturedOutput output) {
    server.expect(requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\": true, \"authorization_code\": \"auth-123\"}",
            MediaType.APPLICATION_JSON));

    bankClient.processPayment(request());

    assertFalse(output.getAll().contains("2222405343248877"), "PAN leaked into logs");
    assertTrue(output.getAll().contains("****8877"), "expected the masked form to be logged");
    assertTrue(output.getAll().contains("cvv='***'"), "expected the CVV to be masked");
  }

  @Test
  void transportFailureBecomesBankCommunicationException() {
    server.expect(requestTo(BANK_URL))
        .andRespond(req -> {
          throw new IOException("connection refused");
        });

    assertThrows(BankCommunicationException.class, () -> bankClient.processPayment(request()));
  }
}
