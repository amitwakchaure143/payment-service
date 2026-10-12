package com.example.paymentservice.service;

import com.example.paymentservice.acquirer.AcquirerClient;
import com.example.paymentservice.acquirer.AcquirerResult;
import com.example.paymentservice.api.PaymentResponse;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.domain.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AcquirerClient acquirerClient;
    private final TransactionTemplate transactionTemplate;

    // The acquirer call runs between two transactions so it never holds a DB connection or lock.
    public PaymentResponse authorize(String merchantId, String idempotencyKey, long amount, String currency) {
        PaymentCreationOutcome outcome = createOrLoadPending(merchantId, idempotencyKey, amount, currency);

        if (!outcome.isNew()) {
            return PaymentResponse.from(handleReplay(outcome.payment(), amount, currency));
        }

        Payment pending = outcome.payment();
        log.info("Payment {} for merchant {} created as PENDING", pending.getId(), merchantId);
        AcquirerResult result = callAcquirer(pending, amount, currency);

        return transactionTemplate.execute(status -> {
            Payment managed = paymentRepository.findById(pending.getId())
                    .orElseThrow(() -> new PaymentNotFoundException(pending.getId()));
            applyAuthorizationResult(managed, result);
            log.info("Payment {} for merchant {} transitioned to {}", managed.getId(), merchantId, managed.getStatus());
            return PaymentResponse.from(managed);
        });
    }

    // Treat unexpected exceptions as Error so the payment is never left PENDING.
    private AcquirerResult callAcquirer(Payment pending, long amount, String currency) {
        try {
            return acquirerClient.authorize(pending.getId().toString(), amount, currency);
        } catch (RuntimeException e) {
            log.error("Acquirer authorize call failed unexpectedly for payment {} merchant {}",
                    pending.getId(), pending.getMerchantId(), e);
            return new AcquirerResult.Error(e.getMessage());
        }
    }

    public PaymentResponse getById(String merchantId, Long id) {
        return paymentRepository.findById(id)
                // Merchant-scoped so payment ids can't be enumerated.
                .filter(payment -> payment.getMerchantId().equals(merchantId))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    // Insert first and rely on the unique constraint, so idempotency holds across instances.
    private PaymentCreationOutcome createOrLoadPending(String merchantId, String idempotencyKey, long amount,
                                                         String currency) {
        try {
            Payment saved = transactionTemplate.execute(status ->
                    paymentRepository.saveAndFlush(new Payment(merchantId, idempotencyKey, amount, currency)));
            return new PaymentCreationOutcome(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Caught outside the transaction, which is already marked rollback-only.
            Payment existing = paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .orElseThrow(() -> e);
            return new PaymentCreationOutcome(existing, false);
        }
    }

    // PENDING -> 409, different payload -> 422, otherwise replay without calling the acquirer.
    private Payment handleReplay(Payment existing, long amount, String currency) {
        if (existing.getStatus() == PaymentStatus.PENDING) {
            throw new PaymentInProgressException(
                    "Payment with idempotency key " + existing.getIdempotencyKey() + " is still being processed");
        }
        if (existing.getAmount() != amount || !existing.getCurrency().equals(currency)) {
            throw new IdempotencyPayloadMismatchException(
                    "Idempotency key " + existing.getIdempotencyKey() + " was reused with a different payload");
        }
        return existing;
    }

    // Store fixed codes, not the acquirer's free-text reason.
    private void applyAuthorizationResult(Payment payment, AcquirerResult result) {
        switch (result) {
            case AcquirerResult.Approved approved -> payment.authorize(approved.reference());
            case AcquirerResult.Declined declined -> payment.fail("DECLINED");
            case AcquirerResult.Error error -> payment.fail("ACQUIRER_ERROR");
        }
    }

    private record PaymentCreationOutcome(Payment payment, boolean isNew) {
    }
}
