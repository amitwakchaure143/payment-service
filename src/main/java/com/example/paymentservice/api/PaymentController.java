package com.example.paymentservice.api;

import com.example.paymentservice.service.CaptureService;
import com.example.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
@RequiredArgsConstructor
public class PaymentController {

    private static final int MAX_HEADER_LENGTH = 100;

    private final PaymentService paymentService;
    private final CaptureService captureService;

    @Operation(summary = "Authorize a payment",
            description = "Amount 6666 is declined by the mock acquirer, 9999 triggers an acquirer error, "
                    + "any other amount is approved. Reuse the same headers and body to see a replay; "
                    + "reuse Idempotency-Key with a different body to see a 422.")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("X-Merchant-Id") @NotBlank @Size(max = MAX_HEADER_LENGTH) String merchantId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = MAX_HEADER_LENGTH) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.authorize(merchantId, idempotencyKey, request.amount(), request.currency()));
    }

    @Operation(summary = "Capture an authorized payment (partially or fully)",
            description = "Payment must be AUTHORIZED; amount must be <= remainingAmount, else 422. "
                    + "Capture amount 6666/9999 hit the same mock-acquirer outcomes as authorize.")
    @PostMapping("/{id}/captures")
    public ResponseEntity<CaptureResponse> capturePayment(
            @RequestHeader("X-Merchant-Id") @NotBlank @Size(max = MAX_HEADER_LENGTH) String merchantId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = MAX_HEADER_LENGTH) String idempotencyKey,
            @PathVariable Long id,
            @Valid @RequestBody CapturePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(captureService.capture(merchantId, id, idempotencyKey, request.amount()));
    }

    @Operation(summary = "Fetch a payment by id",
            description = "Scoped to the calling merchant: a payment owned by a different merchant returns 404.")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @RequestHeader("X-Merchant-Id") @NotBlank @Size(max = MAX_HEADER_LENGTH) String merchantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getById(merchantId, id));
    }
}
