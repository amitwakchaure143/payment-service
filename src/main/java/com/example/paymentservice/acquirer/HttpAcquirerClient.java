package com.example.paymentservice.acquirer;

import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class HttpAcquirerClient implements AcquirerClient {

    private final RestClient acquirerRestClient;

    @Override
    public AcquirerResult authorize(String idempotencyReference, long amount, String currency) {
        return charge("/authorizations", idempotencyReference, new AcquirerRequest(amount, currency));
    }

    @Override
    public AcquirerResult capture(String idempotencyReference, long amount) {
        return charge("/captures", idempotencyReference, new AcquirerRequest(amount, null));
    }

    private AcquirerResult charge(String path, String idempotencyReference, AcquirerRequest request) {
        try {
            AcquirerResponse response = acquirerRestClient.post()
                    .uri(path)
                    .header("Idempotency-Reference", idempotencyReference)
                    .body(request)
                    .retrieve()
                    .body(AcquirerResponse.class);
            if (response == null) {
                return new AcquirerResult.Error("Acquirer returned an empty response");
            }
            return toResult(response);
        } catch (RestClientException e) {
            // Any RestClient failure (non-2xx, timeout, malformed body) becomes an Error.
            return new AcquirerResult.Error(e.getMessage());
        }
    }

    private AcquirerResult toResult(AcquirerResponse response) {
        // case null: a missing status must map to Error, not throw an NPE.
        return switch (response.status()) {
            case "APPROVED" -> new AcquirerResult.Approved(response.reference());
            case "DECLINED" -> new AcquirerResult.Declined(response.reason());
            case null, default -> new AcquirerResult.Error("Unexpected acquirer status: " + response.status());
        };
    }
}
