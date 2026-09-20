package com.checkout.payment.gateway.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint for the expiry month+year combination, a cross-field rule that
 * single-field annotations cannot express. Keeping it inside the Bean Validation pipeline
 * means the controller needs nothing beyond {@code @Valid}, and violations surface through
 * the same 400 handling as field-level errors.
 */
@Documented
@Constraint(validatedBy = ExpiryInFutureValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpiryInFuture {

  String message() default "Card expiry date must be in the future";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
