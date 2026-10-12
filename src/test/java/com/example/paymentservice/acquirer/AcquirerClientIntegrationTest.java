package com.example.paymentservice.acquirer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcquirerClientIntegrationTest {

    @LocalServerPort
    private int port;

    private AcquirerClient acquirerClient;

    // Built here because the app's own client is wired to acquirer.base-url before the random port is known.
    @BeforeEach
    void setUp() {
        acquirerClient = new HttpAcquirerClient(RestClient.create("http://localhost:" + port + "/mock-acquirer"));
    }

    @Test
    void shouldApprove_whenAmountIsNotSpecialCased() {
        AcquirerResult result = acquirerClient.authorize("ref-1", 1000, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Approved.class);
        assertThat(((AcquirerResult.Approved) result).reference()).isNotBlank();
    }

    @Test
    void shouldDecline_whenAmountIs6666() {
        AcquirerResult result = acquirerClient.authorize("ref-2", 6666, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Declined.class);
    }

    @Test
    void shouldReturnError_whenAmountIs9999() {
        AcquirerResult result = acquirerClient.authorize("ref-3", 9999, "USD");

        assertThat(result).isInstanceOf(AcquirerResult.Error.class);
    }

    @Test
    void shouldApproveCapture_whenAmountIsNotSpecialCased() {
        AcquirerResult result = acquirerClient.capture("cap-ref-1", 500);

        assertThat(result).isInstanceOf(AcquirerResult.Approved.class);
    }
}
