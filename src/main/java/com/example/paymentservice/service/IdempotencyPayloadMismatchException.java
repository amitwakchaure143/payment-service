package com.example.paymentservice.service;

import lombok.experimental.StandardException;

// Idempotency key reused with a different body. Mapped to 422.
@StandardException
public class IdempotencyPayloadMismatchException extends RuntimeException {
}
