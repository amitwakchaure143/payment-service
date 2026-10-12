package com.example.paymentservice.service;

// Also used for another merchant's payment, so ids can't be enumerated.
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment not found: " + paymentId);
    }
}
