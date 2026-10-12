package com.example.paymentservice.acquirer;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// In-process stand-in for a real acquirer; the outcome depends only on the amount.
@Hidden
@RestController
@RequestMapping("/mock-acquirer")
public class MockAcquirerController {

    private static final long DECLINED_AMOUNT = 6666L;
    private static final long ERROR_AMOUNT = 9999L;

    @PostMapping("/authorizations")
    public ResponseEntity<AcquirerResponse> authorize(@RequestBody AcquirerRequest request) {
        return respond(request.amount());
    }

    @PostMapping("/captures")
    public ResponseEntity<AcquirerResponse> capture(@RequestBody AcquirerRequest request) {
        return respond(request.amount());
    }

    private ResponseEntity<AcquirerResponse> respond(long amount) {
        if (amount == ERROR_AMOUNT) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (amount == DECLINED_AMOUNT) {
            return ResponseEntity.ok(new AcquirerResponse("DECLINED", null, "Payment declined by acquirer"));
        }
        return ResponseEntity.ok(new AcquirerResponse("APPROVED", UUID.randomUUID().toString(), null));
    }
}
