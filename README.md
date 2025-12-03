### Payment Service Proxy Demo

This project demonstrates a Payment Service Proxy pattern (like a simplified Stripe-like façade) built with Spring Boot.

Key capabilities:
- Proxy pattern encapsulating cross-cutting concerns: validation, idempotency, persistence, webhooks, logging
- Strategy-based provider selection: explicit provider hint or automatic BIN routing
- Two demo providers: `stripe` and `visa`
- Idempotency using an `idempotencyKey` stored in the `Transaction` table

### Architecture Overview
- Controller: `PaymentController` exposes `/api/v1/payments` endpoint
- Proxy: `PaymentServiceProxy` implements cross-cutting concerns and routes to providers
- Providers: `StripePaymentService`, `VisaPaymentService` implement `PaymentProvider`
- Persistence: JPA entities `Client`, `Transaction` (with `providerTransactionId`), repositories
- Webhooks: `WebhookService` simulates event dispatch and keeps an in-memory queue

### Request Model
Authentication headers (required):
- `X-Client-Id`
- `X-Client-Secret`

Request body (`PaymentRequest`):
- `amount` (number)
- `cardNumber` (string)
- `cvv` (string)
- `expiryDate` (string, MM/YY)
- `provider`? (optional, e.g., `stripe` or `visa`)
- `metadata`? (optional object of string->string; e.g., `productId`, `description`, `quantity`, or any custom keys)

Provider selection:
- If `provider` is provided, proxy routes to that provider (`stripe`|`visa`).
- Otherwise, fallback to BIN rule: cards starting with `4111` go to `visa`; others go to `stripe`.

Idempotency:
- The proxy derives an internal idempotency key (not supplied by clients) using a deterministic hash of key request attributes and headers. Repeated identical requests will return a cached response.

### Running locally
1. Ensure a MySQL instance is available or update `src/main/resources/application.properties` accordingly.
2. Build the project:
   - `./gradlew build`
3. Run the app:
   - `./gradlew bootRun`

### Example cURL
```
curl -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Id: demo-client' \
  -H 'X-Client-Secret: demo-secret' \
  -d '{
    "amount": 120.50,
    "cardNumber": "4111111111111111",
    "cvv": "123",
    "expiryDate": "12/29",
    "provider": "visa",
    "metadata": {
      "productId": "prod_123",
      "description": "Test purchase",
      "quantity": "1"
    }
  }'
```

### Notes
- Idempotency is handled internally by the proxy based on request identity; clients should not send `idempotencyKey`.
- Webhooks are simulated and stored in-memory via `WebhookService#getEvents()` for inspection.
