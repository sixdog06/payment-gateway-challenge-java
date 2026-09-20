package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises every validation rule on PostPaymentRequest directly through the Bean Validation
 * engine — no Spring context needed, which keeps these fast.
 */
class PostPaymentRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private PostPaymentRequest validRequest() {
    YearMonth nextMonth = YearMonth.now().plusMonths(1);
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(nextMonth.getMonthValue());
    request.setExpiryYear(nextMonth.getYear());
    request.setCurrency("GBP");
    request.setAmount(1050);
    request.setCvv("123");
    return request;
  }

  private Set<ConstraintViolation<PostPaymentRequest>> validate(PostPaymentRequest request) {
    return validator.validate(request);
  }

  @Test
  void validRequestPasses() {
    assertTrue(validate(validRequest()).isEmpty());
  }

  @Test
  void cardExpiringThisMonthIsValid() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(YearMonth.now().getMonthValue());
    request.setExpiryYear(YearMonth.now().getYear());
    assertTrue(validate(request).isEmpty());
  }

  @Test
  void cardExpiredLastMonthIsInvalid() {
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(lastMonth.getMonthValue());
    request.setExpiryYear(lastMonth.getYear());
    assertEquals(1, validate(request).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567890123", "12345678901234567890", "abcdabcdabcdab"})
  void invalidCardNumbersAreRejected(String cardNumber) {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(cardNumber);
    assertEquals(1, validate(request).size());
  }

  @Test
  void missingFieldsAreRejected() {
    // All-null request: every field-level rule fires; the class-level expiry rule stays
    // silent on nulls so violations are not duplicated.
    assertEquals(6, validate(new PostPaymentRequest()).size());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 13})
  void outOfRangeExpiryMonthsAreRejected(int month) {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(month);
    assertEquals(1, validate(request).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"JPY", "gbp", "GB", "GBPX"})
  void unsupportedCurrenciesAreRejected(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);
    assertEquals(1, validate(request).size());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1050})
  void nonPositiveAmountsAreRejected(int amount) {
    PostPaymentRequest request = validRequest();
    request.setAmount(amount);
    assertEquals(1, validate(request).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"12", "12345", "12a"})
  void invalidCvvsAreRejected(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);
    assertEquals(1, validate(request).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "1234"})
  void threeAndFourDigitCvvsAreValid(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);
    assertTrue(validate(request).isEmpty());
  }
}
