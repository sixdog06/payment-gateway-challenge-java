package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DateTimeException;
import java.time.YearMonth;

/**
 * A card remains valid through the end of its expiry month, so the expiry is invalid only
 * when month+year is strictly before the current {@link YearMonth}.
 */
public class ExpiryInFutureValidator implements ConstraintValidator<ExpiryInFuture, PostPaymentRequest> {

  @Override
  public boolean isValid(PostPaymentRequest request, ConstraintValidatorContext context) {
    // Missing or out-of-range fields are reported by the field-level constraints;
    // this constraint abstains unless there is a well-formed combination to judge.
    if (request == null || request.getExpiryMonth() == null || request.getExpiryYear() == null
        || request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      return true;
    }
    try {
      return !YearMonth.of(request.getExpiryYear(), request.getExpiryMonth())
          .isBefore(YearMonth.now());
    } catch (DateTimeException e) {
      return false;
    }
  }
}
