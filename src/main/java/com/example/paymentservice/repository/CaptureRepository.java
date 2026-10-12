package com.example.paymentservice.repository;

import com.example.paymentservice.domain.Capture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaptureRepository extends JpaRepository<Capture, Long> {

    Optional<Capture> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
}
