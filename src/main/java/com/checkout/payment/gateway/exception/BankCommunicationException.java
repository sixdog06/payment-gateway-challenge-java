package com.checkout.payment.gateway.exception;

/**
 * Thrown when the call to the acquiring bank fails at the transport level: bank 5xx,
 * connection refused, or timeout. Mapped to 502 by the exception handler. Never raised for a
 * bank decline — a decline is a successful 200 response carrying authorized=false.
 */
public class BankCommunicationException extends RuntimeException {

  public BankCommunicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
