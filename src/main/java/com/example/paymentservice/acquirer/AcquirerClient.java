package com.example.paymentservice.acquirer;

// Never throws: declines and transport failures are returned as AcquirerResult.
public interface AcquirerClient {

    AcquirerResult authorize(String idempotencyReference, long amount, String currency);

    AcquirerResult capture(String idempotencyReference, long amount);
}
