package com.example.paymentservice.service;

import com.example.paymentservice.acquirer.AcquirerClient;
import com.example.paymentservice.acquirer.AcquirerResult;
import com.example.paymentservice.api.CaptureResponse;
import com.example.paymentservice.domain.Capture;
import com.example.paymentservice.domain.InvalidPaymentStateException;
import com.example.paymentservice.domain.OverCaptureException;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.domain.PaymentStatus;
import com.example.paymentservice.repository.CaptureRepository;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptureService {

    private final PaymentRepository paymentRepository;
    private final CaptureRepository captureRepository;
    private final AcquirerClient acquirerClient;
    private final TransactionTemplate transactionTemplate;

    // Holds the payment row lock across the acquirer call so concurrent captures serialize.
    public CaptureResponse capture(String merchantId, Long paymentId, String idempotencyKey, long amount) {
        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .filter(p -> p.getMerchantId().equals(merchantId))
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            // Checked under the lock so a replay and a new capture can't race.
            Optional<Capture> existing = captureRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            if (existing.isPresent()) {
                return CaptureResponse.from(replay(existing.get(), paymentId, amount));
            }

            if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
                throw new InvalidPaymentStateException(
                        "Payment must be AUTHORIZED to capture, was " + payment.getStatus());
            }
            // Checked before the acquirer call so a rejected over-capture needs no refund.
            if (amount > payment.remainingAmount()) {
                throw new OverCaptureException(
                        "Capture amount " + amount + " exceeds remaining amount " + payment.remainingAmount());
            }

            // Payment id prefix makes the per-merchant key unique at the acquirer.
            AcquirerResult result = acquirerClient.capture(paymentId + ":" + idempotencyKey, amount);
            Capture capture = captureRepository.saveAndFlush(
                    applyCaptureResult(payment, paymentId, merchantId, idempotencyKey, amount, result));
            log.info("Capture {} for payment {} merchant {} recorded as {}",
                    capture.getId(), paymentId, merchantId, capture.getStatus());
            return CaptureResponse.from(capture);
        });
    }

    private Capture replay(Capture existing, Long paymentId, long amount) {
        if (!existing.getPaymentId().equals(paymentId) || existing.getAmount() != amount) {
            throw new IdempotencyPayloadMismatchException(
                    "Idempotency key " + existing.getIdempotencyKey() + " was reused with a different payload");
        }
        return existing;
    }

    // Only an approved capture changes the payment's capturedAmount.
    private Capture applyCaptureResult(Payment payment, Long paymentId, String merchantId, String idempotencyKey,
                                        long amount, AcquirerResult result) {
        return switch (result) {
            case AcquirerResult.Approved approved -> {
                payment.capture(amount);
                yield Capture.succeeded(paymentId, merchantId, idempotencyKey, amount);
            }
            case AcquirerResult.Declined declined ->
                    Capture.failed(paymentId, merchantId, idempotencyKey, amount, "DECLINED");
            case AcquirerResult.Error error ->
                    Capture.failed(paymentId, merchantId, idempotencyKey, amount, "ACQUIRER_ERROR");
        };
    }
}
