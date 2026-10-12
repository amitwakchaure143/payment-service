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

import java.time.Instant;
import java.util.Objects;

// No PENDING state: a Capture is only written once its outcome is known.
@Entity
@Table(name = "captures",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; never used directly
public class Capture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CaptureStatus status;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Capture(Long paymentId, String merchantId, String idempotencyKey, long amount,
                     CaptureStatus status, String failureReason, Instant createdAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.merchantId = Objects.requireNonNull(merchantId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.amount = amount;
        this.status = Objects.requireNonNull(status);
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Capture succeeded(Long paymentId, String merchantId, String idempotencyKey, long amount) {
        return new Capture(paymentId, merchantId, idempotencyKey, amount, CaptureStatus.SUCCEEDED, null, Instant.now());
    }

    public static Capture failed(Long paymentId, String merchantId, String idempotencyKey, long amount, String failureReason) {
        return new Capture(paymentId, merchantId, idempotencyKey, amount, CaptureStatus.FAILED,
                Objects.requireNonNull(failureReason), Instant.now());
    }
}
