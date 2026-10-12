package com.example.paymentservice.api;

import com.example.paymentservice.acquirer.AcquirerClient;
import com.example.paymentservice.acquirer.AcquirerResult;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private AcquirerClient acquirerClient;

    // --- authorize ---

    @Test
    void shouldAuthorize_whenAcquirerApproves() throws Exception {
        approveAuthorizations();

        authorize("merchant-A", "key-A1", 1000, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.capturedAmount").value(0))
                .andExpect(jsonPath("$.remainingAmount").value(1000))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void shouldFailWithDeclined_whenAcquirerDeclines() throws Exception {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Declined("insufficient funds"));

        authorize("merchant-A", "key-A2", 500, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("DECLINED"));
    }

    @Test
    void shouldFailWithAcquirerError_whenAcquirerErrors() throws Exception {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Error("timeout"));

        authorize("merchant-A", "key-A3", 500, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("ACQUIRER_ERROR"));
    }

    @Test
    void shouldFinalizeAsFailed_whenAcquirerThrowsUnexpectedly() throws Exception {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willThrow(new RuntimeException("boom"));

        authorize("merchant-Z", "key-Z1", 500, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("ACQUIRER_ERROR"));
    }

    @Test
    void shouldReplayAndCallAcquirerOnce_whenAuthorizeRetriedWithSameKeyAndPayload() throws Exception {
        approveAuthorizations();

        String firstResponse = authorize("merchant-B", "key-B1", 750, "USD")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        authorize("merchant-B", "key-B1", 750, "USD")
                .andExpect(status().isCreated())
                .andExpect(content().json(firstResponse));

        verify(acquirerClient, times(1)).authorize(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldReplayFailedResult_whenDeclinedAuthorizeRetriedWithSameKey() throws Exception {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Declined("insufficient funds"));
        authorize("merchant-B", "key-B2", 750, "USD").andExpect(status().isCreated());

        authorize("merchant-B", "key-B2", 750, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("DECLINED"));

        verify(acquirerClient, times(1)).authorize(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldReturn422_whenAuthorizeKeyReusedWithDifferentAmount() throws Exception {
        approveAuthorizations();
        authorize("merchant-C", "key-C1", 200, "USD").andExpect(status().isCreated());

        authorize("merchant-C", "key-C1", 300, "USD")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldReturn422_whenAuthorizeKeyReusedWithDifferentCurrency() throws Exception {
        approveAuthorizations();
        authorize("merchant-C", "key-C2", 200, "USD").andExpect(status().isCreated());

        authorize("merchant-C", "key-C2", 200, "EUR")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldReturn409AndNotCallAcquirer_whenOriginalPaymentIsStillPending() throws Exception {
        paymentRepository.saveAndFlush(new Payment("merchant-P", "key-P1", 500, "USD"));

        authorize("merchant-P", "key-P1", 500, "USD")
                .andExpect(status().isConflict());

        verify(acquirerClient, never()).authorize(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldCreateSeparatePayments_whenDifferentMerchantsUseSameKey() throws Exception {
        approveAuthorizations();
        long first = createAuthorizedPayment("merchant-Q1", "shared-key", 500);

        long second = createAuthorizedPayment("merchant-Q2", "shared-key", 500);

        assertThat(second).isNotEqualTo(first);
        verify(acquirerClient, times(2)).authorize(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldReturn400_whenCurrencyIsNotThreeUppercaseLetters() throws Exception {
        authorize("merchant-V", "key-V1", 500, "usd1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("currency")));
    }

    @Test
    void shouldReturn400_whenAmountIsNotPositive() throws Exception {
        authorize("merchant-V", "key-V2", 0, "USD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")));
    }

    @Test
    void shouldReturn400_whenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Merchant-Id", "merchant-V")
                        .header("Idempotency-Key", "key-V3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Merchant-Id", "merchant-V")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(500, "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
    }

    @Test
    void shouldReturn400_whenMerchantHeaderIsBlank() throws Exception {
        authorize("", "key-blank-1", 500, "USD")
                .andExpect(status().isBadRequest());
    }

    // --- capture ---

    @Test
    void shouldReachCaptured_whenPartialThenFullCapture() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-D", "key-D1", 1000);

        capture("merchant-D", paymentId, "cap-key-D1", 600)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.amount").value(600))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        getPayment("merchant-D", paymentId)
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.capturedAmount").value(600))
                .andExpect(jsonPath("$.remainingAmount").value(400));

        capture("merchant-D", paymentId, "cap-key-D2", 400)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        getPayment("merchant-D", paymentId)
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.capturedAmount").value(1000))
                .andExpect(jsonPath("$.remainingAmount").value(0));
    }

    @Test
    void shouldSendPaymentScopedReferenceToAcquirer_whenCapturing() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-R", "key-R1", 1000);

        capture("merchant-R", paymentId, "cap-key-R1", 100).andExpect(status().isCreated());

        verify(acquirerClient).capture(eq(paymentId + ":cap-key-R1"), eq(100L));
    }

    @Test
    void shouldReturn422_whenCaptureExceedsRemainingAmount() throws Exception {
        approveAuthorizations();
        long paymentId = createAuthorizedPayment("merchant-E", "key-E1", 500);

        capture("merchant-E", paymentId, "cap-key-E1", 600)
                .andExpect(status().isUnprocessableEntity());

        verify(acquirerClient, never()).capture(anyString(), anyLong());
    }

    @Test
    void shouldReturn409_whenCapturingFailedPayment() throws Exception {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Declined("insufficient funds"));
        long paymentId = createAuthorizedPayment("merchant-F", "key-F1", 500);

        capture("merchant-F", paymentId, "cap-key-F1", 100)
                .andExpect(status().isConflict());

        verify(acquirerClient, never()).capture(anyString(), anyLong());
    }

    @Test
    void shouldReturn409_whenCapturingFullyCapturedPaymentWithNewKey() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-F", "key-F2", 500);
        capture("merchant-F", paymentId, "cap-key-F2", 500).andExpect(status().isCreated());

        capture("merchant-F", paymentId, "cap-key-F3", 1)
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotDoubleCount_whenCaptureReplayedWithSameKey() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-G", "key-G1", 1000);

        String firstResponse = capture("merchant-G", paymentId, "cap-key-G1", 300)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        capture("merchant-G", paymentId, "cap-key-G1", 300)
                .andExpect(status().isCreated())
                .andExpect(content().json(firstResponse));

        getPayment("merchant-G", paymentId)
                .andExpect(jsonPath("$.capturedAmount").value(300));
        verify(acquirerClient, times(1)).capture(anyString(), anyLong());
    }

    @Test
    void shouldReplayOriginalCapture_whenRetriedAfterPaymentFullyCaptured() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-G", "key-G2", 500);
        capture("merchant-G", paymentId, "cap-key-G2", 500).andExpect(status().isCreated());

        capture("merchant-G", paymentId, "cap-key-G2", 500)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        verify(acquirerClient, times(1)).capture(anyString(), anyLong());
    }

    @Test
    void shouldReturn422_whenCaptureKeyReusedWithDifferentAmount() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long paymentId = createAuthorizedPayment("merchant-K", "key-K1", 1000);
        capture("merchant-K", paymentId, "cap-key-K1", 300).andExpect(status().isCreated());

        capture("merchant-K", paymentId, "cap-key-K1", 400)
                .andExpect(status().isUnprocessableEntity());

        getPayment("merchant-K", paymentId)
                .andExpect(jsonPath("$.capturedAmount").value(300));
    }

    @Test
    void shouldReturn422_whenCaptureKeyReusedOnDifferentPayment() throws Exception {
        approveAuthorizations();
        approveCaptures();
        long firstPaymentId = createAuthorizedPayment("merchant-K", "key-K2", 1000);
        long secondPaymentId = createAuthorizedPayment("merchant-K", "key-K3", 1000);
        capture("merchant-K", firstPaymentId, "cap-key-K2", 300).andExpect(status().isCreated());

        capture("merchant-K", secondPaymentId, "cap-key-K2", 300)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldLeaveCapturedAmountUnchanged_whenCaptureDeclinedByAcquirer() throws Exception {
        approveAuthorizations();
        given(acquirerClient.capture(anyString(), anyLong()))
                .willReturn(new AcquirerResult.Declined("insufficient funds"));
        long paymentId = createAuthorizedPayment("merchant-H", "key-H1", 1000);

        capture("merchant-H", paymentId, "cap-key-H1", 300)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("DECLINED"));

        getPayment("merchant-H", paymentId)
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.capturedAmount").value(0));
    }

    @Test
    void shouldLeaveCapturedAmountUnchanged_whenCaptureHitsAcquirerError() throws Exception {
        approveAuthorizations();
        given(acquirerClient.capture(anyString(), anyLong()))
                .willReturn(new AcquirerResult.Error("timeout"));
        long paymentId = createAuthorizedPayment("merchant-H", "key-H2", 1000);

        capture("merchant-H", paymentId, "cap-key-H2", 300)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("ACQUIRER_ERROR"));

        getPayment("merchant-H", paymentId)
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.capturedAmount").value(0));
    }

    @Test
    void shouldReturn404_whenCapturingUnknownPayment() throws Exception {
        capture("merchant-L", 999_999L, "cap-key-L1", 100)
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404AndNotCallAcquirer_whenCapturingAnotherMerchantsPayment() throws Exception {
        approveAuthorizations();
        long paymentId = createAuthorizedPayment("merchant-L", "key-L2", 1000);

        capture("merchant-M", paymentId, "cap-key-L2", 100)
                .andExpect(status().isNotFound());

        verify(acquirerClient, never()).capture(anyString(), anyLong());
    }

    @Test
    void shouldReturn400_whenCaptureAmountIsNotPositive() throws Exception {
        capture("merchant-L", 1L, "cap-key-L3", -5)
                .andExpect(status().isBadRequest());
    }

    // --- get ---

    @Test
    void shouldReturn404_whenFetchingAnotherMerchantsPayment() throws Exception {
        approveAuthorizations();
        long paymentId = createAuthorizedPayment("merchant-I", "key-I1", 500);

        getPayment("merchant-J", paymentId)
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400_whenFetchingWithoutMerchantHeader() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenPaymentIdIsNotNumeric() throws Exception {
        getPayment("merchant-I", "abc")
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private void approveAuthorizations() {
        given(acquirerClient.authorize(anyString(), anyLong(), anyString()))
                .willReturn(new AcquirerResult.Approved("acq-ref"));
    }

    private void approveCaptures() {
        given(acquirerClient.capture(anyString(), anyLong()))
                .willReturn(new AcquirerResult.Approved("cap-ref"));
    }

    private long createAuthorizedPayment(String merchantId, String idempotencyKey, long amount) throws Exception {
        String response = authorize(merchantId, idempotencyKey, amount, "USD")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions authorize(String merchantId, String idempotencyKey, long amount, String currency)
            throws Exception {
        return mockMvc.perform(post("/api/v1/payments")
                .header("X-Merchant-Id", merchantId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentBody(amount, currency)));
    }

    private ResultActions capture(String merchantId, long paymentId, String idempotencyKey, long amount)
            throws Exception {
        return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/captures")
                .header("X-Merchant-Id", merchantId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }

    private ResultActions getPayment(String merchantId, Object paymentId) throws Exception {
        return mockMvc.perform(get("/api/v1/payments/" + paymentId).header("X-Merchant-Id", merchantId));
    }

    private static String paymentBody(long amount, String currency) {
        return "{\"amount\": " + amount + ", \"currency\": \"" + currency + "\"}";
    }
}
