package com.example.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

// Entity and domain object: state changes only through authorize/fail/capture.
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; never used directly
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "captured_amount", nullable = false)
    private long capturedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "acquirer_reference")
    private String acquirerReference;

    @Column(name = "failure_reason")
    private String failureReason;

    public Payment(String merchantId, String idempotencyKey, long amount, String currency) {
        this.merchantId = Objects.requireNonNull(merchantId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency);
        this.capturedAmount = 0L;
        this.status = PaymentStatus.PENDING;
    }

    public void authorize(String acquirerReference) {
        transitionTo(PaymentStatus.AUTHORIZED);
        this.acquirerReference = Objects.requireNonNull(acquirerReference);
    }

    public void fail(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = Objects.requireNonNull(reason);
    }

    // Re-checked here even though CaptureService checks first.
    public void capture(long captureAmount) {
        if (status != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException(
                    "Payment must be AUTHORIZED to capture, was " + status);
        }
        long newCapturedAmount = capturedAmount + captureAmount;
        if (newCapturedAmount > amount) {
            throw new OverCaptureException(
                    "Capture amount " + captureAmount + " exceeds remaining amount " + remainingAmount());
        }
        this.capturedAmount = newCapturedAmount;
        // Only a capture that uses up the remaining amount moves it to CAPTURED.
        if (this.capturedAmount == this.amount) {
            transitionTo(PaymentStatus.CAPTURED);
        }
    }

    private void transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentStateException("Cannot transition from " + status + " to " + target);
        }
        this.status = target;
    }

    public long remainingAmount() {
        return amount - capturedAmount;
    }
}
