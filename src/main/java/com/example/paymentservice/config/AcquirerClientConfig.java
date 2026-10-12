package com.example.paymentservice.config;

import com.example.paymentservice.acquirer.AcquirerClient;
import com.example.paymentservice.acquirer.HttpAcquirerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AcquirerClientConfig {

    @Bean
    public RestClient acquirerRestClient(RestClient.Builder restClientBuilder,
                                          @Value("${acquirer.base-url}") String baseUrl) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(1))
                .withReadTimeout(Duration.ofSeconds(2));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public AcquirerClient acquirerClient(RestClient acquirerRestClient) {
        return new HttpAcquirerClient(acquirerRestClient);
    }
}
