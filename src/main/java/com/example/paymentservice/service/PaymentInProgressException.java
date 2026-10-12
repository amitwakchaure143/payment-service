package com.example.paymentservice.service;

import lombok.experimental.StandardException;

// Idempotency key reused while the original payment is still PENDING. Mapped to 409.
@StandardException
public class PaymentInProgressException extends RuntimeException {
}
