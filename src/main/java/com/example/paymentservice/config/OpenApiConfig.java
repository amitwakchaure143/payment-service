package com.example.paymentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Service API")
                .version("v1")
                .description("""
                        Authorize-and-capture payment API. Every POST requires the
                        X-Merchant-Id and Idempotency-Key headers.

                        The acquirer called by this service is mocked in-process: an
                        authorize/capture amount of 6666 is always DECLINED, 9999 always
                        triggers an acquirer error (HTTP 500 from the acquirer), and any
                        other amount is APPROVED. Reuse the same X-Merchant-Id and
                        Idempotency-Key with the same body to see a replay (no new
                        acquirer call); reuse the key with a different body to see the
                        422 idempotency-conflict response."""));
    }
}
