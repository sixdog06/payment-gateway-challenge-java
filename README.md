# Payment Gateway

A card payment gateway: a merchant submits a payment, the gateway validates it, forwards it
to the acquiring bank, records the outcome, and lets the merchant retrieve it later by id.

## Architecture

Layered Spring Boot design — thin controller, orchestrating service, and the bank isolated
behind a client seam:

```
POST /payment ────┐
                  ├──► PaymentGatewayController   (thin: HTTP only)
GET /payment/{id}  ┘             │
                                 ▼
                      PaymentGatewayService        (orchestration)
                          │                │
                          ▼                ▼
                      BankClient      PaymentsRepository
                      (RestTemplate,  (in-memory ConcurrentHashMap,
                       bank.url       single-instance scope cut)
                       from config)
                          │
                          ▼
                      Acquiring Bank (Mountebank simulator, :8080)
```

Cross-cutting: Bean Validation at the boundary and one `@ControllerAdvice` for error
mapping.

## Key design considerations and assumptions

- Authorized and Declined both return 200, since both are normal outcomes of a processed
  payment. Rejected (invalid request, bank not called) returns 400. If the bank cannot be
  reached the gateway returns 502 rather than a Declined, because the actual outcome is
  unknown and the two must not be confused during reconciliation.
- Validation uses Bean Validation annotations on the request DTO, plus one class-level
  constraint for the expiry month+year combination, which field annotations cannot express.
  Anything beyond format checks is left to the bank.
- The full card number is only used to call the bank and is never stored or logged. Only
  the last four digits are kept, as a String so leading zeros survive. The CVV is never
  stored.
- Declined payments are stored so they can be retrieved later; rejected ones are not, since
  no payment was created. Storage is an in-memory ConcurrentHashMap as the brief allows;
  data is lost on restart.
- The bank call lives in a separate client package that owns the wire format
  (`expiry_date` as `MM/YYYY`), and the bank URL comes from `application.properties`.
- Error responses use the skeleton's `ErrorResponse` for the cases the payment flow can
  produce. Edge cases like malformed JSON are left to Spring Boot's defaults.
- 37 tests, runnable without Docker; the bank is mocked at the HTTP layer.

## API

Interactive docs (Swagger UI): http://localhost:8090/swagger-ui/index.html

### POST /payment

Request:

```json
{
  "card_number": "2222405343248877",
  "expiry_month": 4,
  "expiry_year": 2027,
  "currency": "GBP",
  "amount": 1050,
  "cvv": "123"
}
```

| Situation | HTTP | Body |
|---|---|---|
| Bank authorized the payment | 200 | `{"id": "...", "status": "Authorized", "card_number_last_four": "8877", "expiry_month": 4, "expiry_year": 2027, "currency": "GBP", "amount": 1050}` |
| Bank declined the payment | 200 | same shape, `status: "Declined"` |
| Validation failed (**Rejected** — bank never called) | 400 | `{"message": "Rejected: validation failed: <every violated rule>"}` |
| Bank unavailable | 502 | `{"message": "Acquiring bank is unavailable; no payment was recorded"}` |

### GET /payment/{id}

| Situation | HTTP | Body |
|---|---|---|
| Payment exists (Authorized or Declined) | 200 | `{"id": "...", "status": "Authorized", "card_number_last_four": "8877", "expiry_month": 4, "expiry_year": 2027, "currency": "GBP", "amount": 1050}` |
| Unknown id (or a Rejected request, which is never stored) | 404 | `{"message": "Payment not found"}` |

### Simulator cheat sheet

| Last digit of `card_number` | Bank response | Gateway response |
|---|---|---|
| odd | 200, authorized | 200 `Authorized` |
| even, non-zero | 200, declined | 200 `Declined` |
| 0 | 503 Service Unavailable (bank down) | 502 Bad Gateway (upstream failed) |

503 means the bank itself is down; the gateway maps it (and any other bank communication
failure) to 502 Bad Gateway — the gateway is up, but its upstream dependency failed, and no
payment was recorded. The gateway never returns 503 itself.
