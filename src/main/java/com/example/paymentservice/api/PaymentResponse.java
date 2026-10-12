package com.example.paymentservice.api;

import com.example.paymentservice.domain.Payment;

public record PaymentResponse(
        Long id,
        String status,
        long amount,
        long capturedAmount,
        long remainingAmount,
        String currency,
        String failureReason) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCapturedAmount(),
                payment.remainingAmount(),
                payment.getCurrency(),
                payment.getFailureReason());
    }
}
