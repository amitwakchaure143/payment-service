package com.example.paymentservice.repository;

import com.example.paymentservice.domain.Capture;
import com.example.paymentservice.domain.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CaptureRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CaptureRepository captureRepository;

    private Long persistAuthorizedPaymentId() {
        Payment payment = new Payment("merchant-1", "pay-key-1", 1000, "USD");
        payment.authorize("acquirer-ref");
        return paymentRepository.saveAndFlush(payment).getId();
    }

    @Test
    void shouldAssignIdAndPersistFields_whenSavingSucceededCapture() {
        Long paymentId = persistAuthorizedPaymentId();
        Capture capture = Capture.succeeded(paymentId, "merchant-1", "cap-key-1", 400);

        Capture saved = captureRepository.saveAndFlush(capture);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void shouldFindByMerchantIdAndIdempotencyKey_whenCaptureExists() {
        Long paymentId = persistAuthorizedPaymentId();
        captureRepository.saveAndFlush(Capture.succeeded(paymentId, "merchant-1", "cap-key-1", 400));

        Optional<Capture> found = captureRepository.findByMerchantIdAndIdempotencyKey("merchant-1", "cap-key-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualTo(400);
    }

    @Test
    void shouldThrowDataIntegrityViolation_whenMerchantIdAndIdempotencyKeyDuplicated() {
        Long paymentId = persistAuthorizedPaymentId();
        captureRepository.saveAndFlush(Capture.succeeded(paymentId, "merchant-1", "cap-key-1", 400));

        assertThatThrownBy(() -> captureRepository.saveAndFlush(
                Capture.succeeded(paymentId, "merchant-1", "cap-key-1", 100)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistFailureReason_whenSavingFailedCapture() {
        Long paymentId = persistAuthorizedPaymentId();
        Capture capture = Capture.failed(paymentId, "merchant-1", "cap-key-1", 400, "ACQUIRER_ERROR");

        Capture saved = captureRepository.saveAndFlush(capture);

        assertThat(saved.getFailureReason()).isEqualTo("ACQUIRER_ERROR");
    }
}
