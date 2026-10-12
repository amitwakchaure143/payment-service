package com.example.paymentservice.domain;

// PENDING -> AUTHORIZED -> CAPTURED, or PENDING -> FAILED. CAPTURED and FAILED are terminal.
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == AUTHORIZED || target == FAILED;
            case AUTHORIZED -> target == CAPTURED;
            case CAPTURED, FAILED -> false;
        };
    }
}
