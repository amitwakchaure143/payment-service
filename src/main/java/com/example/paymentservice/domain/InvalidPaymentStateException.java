package com.example.paymentservice.domain;

import lombok.experimental.StandardException;

// Transition not allowed by PaymentStatus. Mapped to 409.
@StandardException
public class InvalidPaymentStateException extends RuntimeException {
}
