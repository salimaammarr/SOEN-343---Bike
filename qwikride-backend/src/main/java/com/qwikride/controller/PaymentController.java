package com.qwikride.controller;

import com.qwikride.dto.PaymentRequest;
import com.qwikride.dto.PaymentResponse;
import com.qwikride.model.User;
import com.qwikride.prc.billing.BillingLedgerService;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import com.qwikride.repository.UserRepository;
import com.qwikride.service.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173") // Allow frontend access
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final PricingPlanVersionRepository pricingPlanVersionRepository;
    private final BillingLedgerService billingLedgerService;

    @PostMapping("/create-payment-intent")
    public ResponseEntity<PaymentResponse> createPaymentIntent(@RequestBody PaymentRequest paymentRequest) {
        try {
            PaymentResponse response = paymentService.createPaymentIntent(paymentRequest);
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/confirm-plan-update")
    public ResponseEntity<?> confirmPlanUpdate(@RequestBody Map<String, String> payload) {
        String paymentIntentId = payload.get("paymentIntentId");
        if (paymentIntentId == null) {
            return ResponseEntity.badRequest().body("Missing paymentIntentId");
        }

        try {
            log.info("Confirming plan update for paymentIntentId: {}", paymentIntentId);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Map<String, Object> result = paymentService.processPaymentConfirmation(paymentIntentId, username);
            return ResponseEntity.ok(result);

        } catch (StripeException e) {
            log.error("Stripe error", e);
            return ResponseEntity.status(500).body("Stripe error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing payment", e);
            return ResponseEntity.status(500).body("Error processing payment: " + e.getMessage());
        }
    }

    @PostMapping("/switch-plan")
    public ResponseEntity<?> switchPlan(@RequestBody Map<String, String> payload) {
        String planIdStr = payload.get("planId");
        if (planIdStr == null) {
            return ResponseEntity.badRequest().body("Missing planId");
        }

        UUID planId = UUID.fromString(planIdStr);
        PricingPlanVersion plan = pricingPlanVersionRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        // Only allow switching to free plans directly
        if (plan.getSubscriptionPrice() != null
                && plan.getSubscriptionPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return ResponseEntity.badRequest().body("This plan requires payment. Please use the payment flow.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPricingPlan(plan.getPlanType());
        userRepository.save(user);

        billingLedgerService.recordPlanChange(user.getId(), plan, true);

        return ResponseEntity.ok(Map.of(
                "message", "Plan updated successfully to " + plan.getPlanType(),
                "planUpdated", true));
    }
}
