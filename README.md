# payment-service

## Running the application

Requires JDK 21. Nothing else needs to be installed: the database is an in-memory H2 and the acquirer is mocked inside the app.

```
./mvnw spring-boot:run
```

The app starts on port `8080`. Keep that port free, because the app calls its own mock acquirer at `http://localhost:8080/mock-acquirer`.

Data is held in memory and is cleared on every restart.

## Testing with Swagger UI

Once the app is running, open:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

In Swagger UI, expand an endpoint, click **Try it out**, fill in the headers and body, then click **Execute**.

### Headers

Every request needs an `X-Merchant-Id` header, for example `merchant-1`. Every `POST` also needs an `Idempotency-Key` header, for example `key-001`. Use a new key for each new operation.

### 1. Authorize a payment

`POST /api/v1/payments`

```json
{ "amount": 10000, "currency": "USD" }
```

Returns `201` with the payment. Note its `id` for the next steps.

### 2. Capture the payment (full or partial)

`POST /api/v1/payments/{id}/captures`

```json
{ "amount": 4000 }
```

Returns `201`. You can capture more than once, as long as the total does not go over the authorized amount.

### 3. Get the payment

`GET /api/v1/payments/{id}` shows the current status and the captured amount.

### Mock acquirer outcomes

The acquirer's response depends only on the amount, for both authorize and capture:

| Amount | Result |
|---|---|
| `6666` | Declined |
| `9999` | Acquirer error |
| anything else | Approved |

### Scenarios worth trying

- **Idempotent replay:** send the same `POST` again with the same `X-Merchant-Id`, `Idempotency-Key` and body. You get the original response back, and the acquirer is not called again.
- **Key reuse with a different body:** keep the `Idempotency-Key` but change the amount. You get `422`.
- **Over-capture:** try to capture more than the remaining authorized amount. You get `422`.
- **Capture a failed payment:** authorize `6666`, then try to capture it. You get `409`.
- **Another merchant's payment:** `GET` a payment with a different `X-Merchant-Id`. You get `404`.
- **Validation:** leave out a header or send `"amount": 0`. You get `400`.

Errors come back as RFC 9457 `ProblemDetail` JSON.
