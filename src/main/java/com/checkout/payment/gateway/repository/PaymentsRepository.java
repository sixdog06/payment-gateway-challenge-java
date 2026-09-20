package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory payment store, as permitted by the brief. Uses a {@link ConcurrentMap} because
 * requests are handled on multiple threads. Single-instance only — a real deployment would
 * put a database behind this.
 */
@Repository
public class PaymentsRepository {

  private final ConcurrentMap<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();

  public void add(PostPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
