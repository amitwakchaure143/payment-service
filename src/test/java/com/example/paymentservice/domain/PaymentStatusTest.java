package com.example.paymentservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void shouldAllowTransition_whenPendingToAuthorized() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.AUTHORIZED)).isTrue();
    }

    @Test
    void shouldAllowTransition_whenPendingToFailed() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void shouldAllowTransition_whenAuthorizedToCaptured() {
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.CAPTURED)).isTrue();
    }

    @Test
    void shouldRejectTransition_whenPendingToCaptured() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CAPTURED)).isFalse();
    }

    @Test
    void shouldRejectTransition_whenAuthorizedToFailed() {
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void shouldRejectTransition_whenAuthorizedToPending() {
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    void shouldRejectAnyTransition_whenCaptured() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.CAPTURED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void shouldRejectAnyTransition_whenFailed() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.FAILED.canTransitionTo(target)).isFalse();
        }
    }
}
