package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.ExpiryInFuture;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

/**
 * Incoming payment request from a merchant.
 *
 * <p>Numeric fields use boxed types so that missing JSON properties bind as null and are
 * reported by Bean Validation, instead of silently defaulting to 0 as primitives would.
 *
 * <p>The full card number is required to call the acquiring bank but is never persisted.
 * {@link #toString()} masks the PAN (last four only) and the CVV so instances are safe to log.
 */
@ExpiryInFuture
public class PostPaymentRequest implements Serializable {

  @NotBlank(message = "Card number is required")
  @Pattern(regexp = "^\\d{14,19}$", message = "Card number must be 14-19 numeric characters")
  @JsonProperty("card_number")
  private String cardNumber;

  @NotNull(message = "Expiry month is required")
  @Min(value = 1, message = "Expiry month must be between 1 and 12")
  @Max(value = 12, message = "Expiry month must be between 1 and 12")
  @JsonProperty("expiry_month")
  private Integer expiryMonth;

  @NotNull(message = "Expiry year is required")
  @JsonProperty("expiry_year")
  private Integer expiryYear;

  @NotBlank(message = "Currency is required")
  @Pattern(regexp = "USD|GBP|EUR", message = "Currency must be one of: USD, GBP, EUR")
  private String currency;

  @NotNull(message = "Amount is required")
  @Positive(message = "Amount must be a positive integer in the minor currency unit")
  private Integer amount;

  @NotBlank(message = "CVV is required")
  @Pattern(regexp = "^\\d{3,4}$", message = "CVV must be 3-4 numeric characters")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  public String getCardNumberLastFour() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return cardNumber;
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber='****" + getCardNumberLastFour() + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv='***'" +
        '}';
  }
}
