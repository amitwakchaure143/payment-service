package com.example.paymentservice.api;

import com.example.paymentservice.domain.Capture;

public record CaptureResponse(
        Long id,
        Long paymentId,
        long amount,
        String status,
        String failureReason) {

    public static CaptureResponse from(Capture capture) {
        return new CaptureResponse(
                capture.getId(),
                capture.getPaymentId(),
                capture.getAmount(),
                capture.getStatus().name(),
                capture.getFailureReason());
    }
}
