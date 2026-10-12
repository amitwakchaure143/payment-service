package com.example.paymentservice.service;

import com.example.paymentservice.acquirer.AcquirerClient;
import com.example.paymentservice.acquirer.AcquirerResult;
import com.example.paymentservice.domain.CaptureStatus;
import com.example.paymentservice.domain.OverCaptureException;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.repository.CaptureRepository;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CaptureService captureService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CaptureRepository captureRepository;

    @MockitoBean
    private AcquirerClient acquirerClient;

    @Test
    void shouldCaptureExactlyThreeTimes_whenTenThreadsCapture30OnA100Authorization() throws InterruptedException {
        approveAll();
        Long paymentId = paymentService.authorize("merchant-conc-a", "auth-key-a", 100, "USD").id();

        Queue<Throwable> failures = runConcurrently(10, i ->
                captureService.capture("merchant-conc-a", paymentId, "cap-key-a-" + i, 30));

        assertThat(failures).hasSize(7).allMatch(OverCaptureException.class::isInstance);
        Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(reloaded.getCapturedAmount()).isEqualTo(90);
        verify(acquirerClient, times(3)).capture(anyString(), anyLong());
    }

    @Test
    void shouldCreateOnePaymentRow_whenTenThreadsAuthorizeWithSameKey() throws InterruptedException {
        approveAll();

        Queue<Throwable> failures = runConcurrently(10, i ->
                paymentService.authorize("merchant-conc-b", "auth-key-b", 500, "USD"));

        // Losers either replay the payment or get 409 while it is PENDING.
        assertThat(failures).allMatch(PaymentInProgressException.class::isInstance);
        List<Payment> rows = paymentRepository.findAll().stream()
                .filter(p -> p.getMerchantId().equals("merchant-conc-b"))
                .toList();
        assertThat(rows).hasSize(1);
        verify(acquirerClient, times(1)).authorize(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldCountCaptureOnce_whenFiveThreadsCaptureWithSameKey() throws InterruptedException {
        approveAll();
        Long paymentId = paymentService.authorize("merchant-conc-c", "auth-key-c", 100, "USD").id();

        Queue<Throwable> failures = runConcurrently(5, i ->
                captureService.capture("merchant-conc-c", paymentId, "shared-cap-key-c", 30));

        assertThat(failures).isEmpty();
        assertThat(captureRepository.findByMerchantIdAndIdempotencyKey("merchant-conc-c", "shared-cap-key-c"))
                .hasValueSatisfying(capture -> assertThat(capture.getStatus()).isEqualTo(CaptureStatus.SUCCEEDED));
        Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(reloaded.getCapturedAmount()).isEqualTo(30);
        verify(acquirerClient, times(1)).capture(anyString(), anyLong());
    }

    private void approveAll() {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Approved("acq-ref"));
        given(acquirerClient.capture(anyString(), anyLong()))
                .willReturn(new AcquirerResult.Approved("cap-ref"));
    }

    private Queue<Throwable> runConcurrently(int threadCount, IntConsumer task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.accept(index);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).as("all threads finished").isTrue();
        executor.shutdown();
        return failures;
    }
}
