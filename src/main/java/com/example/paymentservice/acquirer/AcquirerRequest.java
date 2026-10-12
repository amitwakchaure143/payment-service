package com.example.paymentservice.acquirer;

// currency is null for captures.
record AcquirerRequest(long amount, String currency) {
}
