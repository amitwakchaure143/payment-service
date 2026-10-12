package com.example.paymentservice.acquirer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAcquirerClientTest {

    private MockRestServiceServer server;
    private HttpAcquirerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://mock-acquirer");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAcquirerClient(builder.build());
    }

    @Test
    void shouldSendIdempotencyReferenceAndBody_whenAuthorizing() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Reference", "ref-0"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andRespond(withSuccess("""
                        {"status": "APPROVED", "reference": "acq-ref-0"}
                        """, MediaType.APPLICATION_JSON));

        client.authorize("ref-0", 1000, "USD");

        server.verify();
    }

    @Test
    void shouldReturnApproved_whenAcquirerApproves() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andRespond(withSuccess("""
                        {"status": "APPROVED", "reference": "acq-ref-1"}
                        """, MediaType.APPLICATION_JSON));

        AcquirerResult result = client.authorize("ref-1", 1000, "USD");

        assertThat(result).isEqualTo(new AcquirerResult.Approved("acq-ref-1"));
    }

    @Test
    void shouldReturnDeclined_whenAcquirerDeclines() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andRespond(withSuccess("""
                        {"status": "DECLINED", "reason": "insufficient funds"}
                        """, MediaType.APPLICATION_JSON));

        AcquirerResult result = client.authorize("ref-2", 6666, "USD");

        assertThat(result).isEqualTo(new AcquirerResult.Declined("insufficient funds"));
    }

    @Test
    void shouldReturnError_whenAcquirerRespondsWithServerError() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andRespond(withServerError());

        AcquirerResult result = client.authorize("ref-3", 9999, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Error.class);
    }

    @Test
    void shouldReturnError_whenAcquirerResponseBodyIsEmpty() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        AcquirerResult result = client.authorize("ref-4", 1000, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Error.class);
    }

    @Test
    void shouldReturnError_whenAcquirerResponseHasNoStatusField() {
        server.expect(requestTo("http://mock-acquirer/authorizations"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        AcquirerResult result = client.authorize("ref-5", 1000, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Error.class);
    }

    @Test
    void shouldPostToCapturesWithReference_whenCapturing() {
        server.expect(requestTo("http://mock-acquirer/captures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Reference", "42:cap-key"))
                .andExpect(jsonPath("$.amount").value(500))
                .andRespond(withSuccess("""
                        {"status": "APPROVED", "reference": "cap-ref-1"}
                        """, MediaType.APPLICATION_JSON));

        AcquirerResult result = client.capture("42:cap-key", 500);

        assertThat(result).isEqualTo(new AcquirerResult.Approved("cap-ref-1"));
        server.verify();
    }
}
