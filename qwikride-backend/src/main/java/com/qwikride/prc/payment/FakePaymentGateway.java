package com.qwikride.prc.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Mock Payment Gateway for testing and development.
 * Simulates various payment scenarios including successes and failures.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);
    private final Random random = new Random();

    @Override
    public PaymentResponse charge(PaymentRequest request) {
        log.info("Processing payment: riderId={}, amount={}, referenceId={}, paymentMethod={}", 
                 request.riderId(), request.amount(), request.referenceId(), request.paymentMethodToken());

        // Simulate processing delay
        try {
            Thread.sleep(500 + random.nextInt(1000)); // 0.5-1.5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Validate amount
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Payment failed: Invalid amount - {}", request.amount());
            return new PaymentResponse(false, null, LocalDateTime.now(), "INVALID_AMOUNT");
        }

        // Validate amount not too high (simulate card limit)
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
            log.warn("Payment failed: Amount exceeds limit - {}", request.amount());
            return new PaymentResponse(false, null, LocalDateTime.now(), "AMOUNT_EXCEEDS_LIMIT");
        }

        // Simulate different payment method scenarios
        String paymentMethod = request.paymentMethodToken();
        if (paymentMethod == null) {
            paymentMethod = "saved-default";
        }

        // Simulate specific failure scenarios based on payment method token
        PaymentResponse failureResponse = simulateFailureScenarios(paymentMethod);
        if (failureResponse != null) {
            log.warn("Payment failed: {}", failureResponse.failureReason());
            return failureResponse;
        }

        // Simulate random failures (5% chance)
        if (random.nextInt(100) < 5) {
            String[] randomFailures = {
                "NETWORK_ERROR",
                "GATEWAY_TIMEOUT",
                "TEMPORARY_ERROR"
            };
            String failure = randomFailures[random.nextInt(randomFailures.length)];
            log.warn("Payment failed: Random failure - {}", failure);
            return new PaymentResponse(false, null, LocalDateTime.now(), failure);
        }

        // Success - generate transaction ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        log.info("Payment successful: transactionId={}, amount={}", transactionId, request.amount());
        
        return new PaymentResponse(
                true,
                transactionId,
                LocalDateTime.now(),
                null
        );
    }

    /**
     * Simulate various payment failure scenarios based on payment method token.
     * This allows testing different failure cases.
     */
    private PaymentResponse simulateFailureScenarios(String paymentMethod) {
        LocalDateTime now = LocalDateTime.now();

        // Test tokens for specific scenarios
        switch (paymentMethod.toLowerCase()) {
            case "insufficient-funds":
            case "test-insufficient":
                return new PaymentResponse(false, null, now, "INSUFFICIENT_FUNDS");
            
            case "expired-card":
            case "test-expired":
                return new PaymentResponse(false, null, now, "CARD_EXPIRED");
            
            case "invalid-card":
            case "test-invalid":
                return new PaymentResponse(false, null, now, "INVALID_CARD");
            
            case "declined":
            case "test-declined":
                return new PaymentResponse(false, null, now, "CARD_DECLINED");
            
            case "fraud-suspected":
            case "test-fraud":
                return new PaymentResponse(false, null, now, "FRAUD_SUSPECTED");
            
            case "network-error":
            case "test-network":
                return new PaymentResponse(false, null, now, "NETWORK_ERROR");
            
            default:
                // No failure for other tokens
                return null;
        }
    }
}
