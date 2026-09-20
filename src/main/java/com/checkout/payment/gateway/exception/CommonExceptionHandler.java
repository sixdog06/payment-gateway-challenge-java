package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Payment not found"),
        HttpStatus.NOT_FOUND);
  }

  /**
   * Bean Validation failure — the brief's Rejected case. Every violated rule is named in the
   * message (field errors as "field: message"; the class-level expiry rule has no field, so
   * its message goes in bare). Nothing was stored and the bank was never called.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
    String errors = ex.getBindingResult().getAllErrors().stream()
        .map(error -> error instanceof FieldError fieldError
            ? fieldError.getField() + ": " + error.getDefaultMessage()
            : error.getDefaultMessage())
        .collect(Collectors.joining("; "));
    LOG.info("Payment request rejected: {}", errors);
    return new ResponseEntity<>(new ErrorResponse("Rejected: validation failed: " + errors),
        HttpStatus.BAD_REQUEST);
  }

  /**
   * The acquiring bank gave no decision (5xx, connection failure, timeout). Mapped to 502 to
   * keep "the bank said no" (200 Declined) distinguishable from "the bank could not be asked" —
   * collapsing the two would break the merchant's reconciliation.
   */
  @ExceptionHandler(BankCommunicationException.class)
  public ResponseEntity<ErrorResponse> handleBankCommunicationException(BankCommunicationException ex) {
    LOG.error("Acquiring bank call failed", ex);
    return new ResponseEntity<>(
        new ErrorResponse("Acquiring bank is unavailable; no payment was recorded"),
        HttpStatus.BAD_GATEWAY);
  }
}
