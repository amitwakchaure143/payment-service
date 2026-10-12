package com.example.paymentservice.acquirer;

// reference is set only for APPROVED, reason only for DECLINED.
record AcquirerResponse(String status, String reference, String reason) {
}
