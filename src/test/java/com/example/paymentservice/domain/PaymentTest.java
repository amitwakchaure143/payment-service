package com.example.paymentservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment authorizedPayment(long amount) {
        Payment payment = new Payment("merchant-1", "key-1", amount, "USD");
        payment.authorize("acquirer-ref");
        return payment;
    }

    @Test
    void shouldAuthorize_whenPending() {
        Payment payment = new Payment("merchant-1", "key-1", 1000, "USD");

        payment.authorize("acquirer-ref");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getAcquirerReference()).isEqualTo("acquirer-ref");
    }

    @Test
    void shouldFail_whenPending() {
        Payment payment = new Payment("merchant-1", "key-1", 1000, "USD");

        payment.fail("DECLINED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("DECLINED");
    }

    @Test
    void shouldThrowInvalidState_whenAuthorizingAlreadyAuthorizedPayment() {
        Payment payment = authorizedPayment(1000);

        assertThatThrownBy(() -> payment.authorize("another-ref"))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldCapturePartially_whenAmountLessThanAuthorized() {
        Payment payment = authorizedPayment(1000);

        payment.capture(400);

        assertThat(payment.getCapturedAmount()).isEqualTo(400);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.remainingAmount()).isEqualTo(600);
    }

    @Test
    void shouldCapture_whenCapturedAmountReachesAuthorizedAmount() {
        Payment payment = authorizedPayment(1000);

        payment.capture(600);
        payment.capture(400);

        assertThat(payment.getCapturedAmount()).isEqualTo(1000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.remainingAmount()).isZero();
    }

    @Test
    void shouldThrowOverCapture_whenAmountExceedsRemaining() {
        Payment payment = authorizedPayment(1000);
        payment.capture(600);

        assertThatThrownBy(() -> payment.capture(500))
                .isInstanceOf(OverCaptureException.class);

        assertThat(payment.getCapturedAmount()).isEqualTo(600);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void shouldThrowInvalidState_whenCapturingPendingPayment() {
        Payment payment = new Payment("merchant-1", "key-1", 1000, "USD");

        assertThatThrownBy(() -> payment.capture(100))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldThrowInvalidState_whenCapturingFailedPayment() {
        Payment payment = new Payment("merchant-1", "key-1", 1000, "USD");
        payment.fail("DECLINED");

        assertThatThrownBy(() -> payment.capture(100))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldThrowInvalidState_whenCapturingAlreadyCapturedPayment() {
        Payment payment = authorizedPayment(1000);
        payment.capture(1000);

        assertThatThrownBy(() -> payment.capture(1))
                .isInstanceOf(InvalidPaymentStateException.class);
    }
}
