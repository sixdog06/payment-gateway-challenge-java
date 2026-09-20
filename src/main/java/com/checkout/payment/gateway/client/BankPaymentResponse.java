package com.checkout.payment.gateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The acquiring bank's decision. A 200 response always carries the {@code authorized} flag;
 * the authorization code is only present when authorized.
 */
public class BankPaymentResponse {

  private boolean authorized;

  @JsonProperty("authorization_code")
  private String authorizationCode;

  public boolean isAuthorized() {
    return authorized;
  }

  public void setAuthorized(boolean authorized) {
    this.authorized = authorized;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }
}
