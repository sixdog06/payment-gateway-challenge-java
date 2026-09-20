package com.checkout.payment.gateway.controller;


import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Full HTTP layer through the real service and repository, with only the acquiring bank
 * mocked — so the suite runs without Docker. Debug logging is enabled for the log-capture
 * assertions below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.checkout=DEBUG")
class PaymentGatewayControllerTest {

  private static final String VALID_BODY = """
      {"card_number":"2222405343248871","expiry_month":4,"expiry_year":2999,
       "currency":"GBP","amount":1050,"cvv":"123"}""";

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  private BankClient bankClient;

  private void bankDecides(boolean authorized) {
    BankPaymentResponse decision = new BankPaymentResponse();
    decision.setAuthorized(authorized);
    decision.setAuthorizationCode(authorized ? "some-code" : "");
    when(bankClient.processPayment(any())).thenReturn(decision);
  }

  @Test
  void whenPaymentIsAuthorizedThen200WithStoredDetails() throws Exception {
    bankDecides(true);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json").content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("8871"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void whenPaymentIsDeclinedThen200WithDeclinedStatus() throws Exception {
    bankDecides(false);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json").content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void whenRequestIsInvalidThen400WithPerFieldErrorsAndBankIsNotCalled() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("""
                {"card_number":"abc","expiry_month":13,"expiry_year":2020,
                 "currency":"JPY","amount":-5,"cvv":"12x"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Rejected: validation failed")))
        .andExpect(jsonPath("$.message", containsString("cardNumber")))
        .andExpect(jsonPath("$.message", containsString("expiryMonth")))
        .andExpect(jsonPath("$.message", containsString("currency")))
        .andExpect(jsonPath("$.message", containsString("amount")))
        .andExpect(jsonPath("$.message", containsString("cvv")));

    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void whenBankIsUnavailableThen502() throws Exception {
    when(bankClient.processPayment(any()))
        .thenThrow(new BankCommunicationException("Acquiring bank is unavailable", null));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json").content(VALID_BODY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message")
            .value("Acquiring bank is unavailable; no payment was recorded"));
  }

  @Test
  void fullCardNumberNeverAppearsInLogs(CapturedOutput output) throws Exception {
    bankDecides(true);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json").content(VALID_BODY))
        .andExpect(status().isOk());

    assertFalse(output.getAll().contains("2222405343248871"), "PAN leaked into logs");
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiry_month").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found"));
  }
}
