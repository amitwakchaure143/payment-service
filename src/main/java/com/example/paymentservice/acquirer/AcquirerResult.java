package com.example.paymentservice.acquirer;

// Sealed so every caller's switch must handle all outcomes.
public sealed interface AcquirerResult {

    record Approved(String reference) implements AcquirerResult {
    }

    record Declined(String reason) implements AcquirerResult {
    }

    record Error(String message) implements AcquirerResult {
    }
}
