package com.example.paymentservice.repository;

import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldAssignIdAndPersistFields_whenSavingNewPayment() {
        Payment payment = new Payment("merchant-1", "key-1", 1000, "USD");

        Payment saved = paymentRepository.saveAndFlush(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldFindByMerchantIdAndIdempotencyKey_whenPaymentExists() {
        paymentRepository.saveAndFlush(new Payment("merchant-1", "key-1", 1000, "USD"));

        Optional<Payment> found = paymentRepository.findByMerchantIdAndIdempotencyKey("merchant-1", "key-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualTo(1000);
    }

    @Test
    void shouldReturnEmpty_whenNoPaymentMatchesMerchantAndKey() {
        Optional<Payment> found = paymentRepository.findByMerchantIdAndIdempotencyKey("merchant-1", "missing-key");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldThrowDataIntegrityViolation_whenMerchantIdAndIdempotencyKeyDuplicated() {
        paymentRepository.saveAndFlush(new Payment("merchant-1", "key-1", 1000, "USD"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment("merchant-1", "key-1", 500, "USD")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldReturnPayment_whenFindByIdForUpdate() {
        Payment saved = paymentRepository.saveAndFlush(new Payment("merchant-1", "key-1", 1000, "USD"));

        Optional<Payment> locked = paymentRepository.findByIdForUpdate(saved.getId());

        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldReturnEmpty_whenFindByIdForUpdateWithUnknownId() {
        Optional<Payment> locked = paymentRepository.findByIdForUpdate(-1L);

        assertThat(locked).isEmpty();
    }
}
