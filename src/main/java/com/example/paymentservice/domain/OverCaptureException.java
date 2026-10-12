package com.example.paymentservice.domain;

import lombok.experimental.StandardException;

// Capture amount exceeds Payment.remainingAmount(). Mapped to 422.
@StandardException
public class OverCaptureException extends RuntimeException {
}
