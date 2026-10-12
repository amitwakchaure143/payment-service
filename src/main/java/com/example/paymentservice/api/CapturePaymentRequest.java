package com.example.paymentservice.api;

import jakarta.validation.constraints.Positive;

public record CapturePaymentRequest(@Positive(message = "amount must be greater than 0") long amount) {
}
