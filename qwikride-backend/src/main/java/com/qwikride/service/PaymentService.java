package com.qwikride.service;

import com.qwikride.dto.PaymentRequest;
import com.qwikride.dto.PaymentResponse;
import com.qwikride.model.User;
import com.qwikride.prc.billing.BillingLedgerService;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.repository.LedgerEntryRepository;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import com.qwikride.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingPlanVersionRepository pricingPlanVersionRepository;

    @Autowired
    private BillingLedgerService billingLedgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public PaymentResponse createPaymentIntent(PaymentRequest paymentRequest) throws StripeException {
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(paymentRequest.getAmount())
                .setCurrency(paymentRequest.getCurrency())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build());

        if (paymentRequest.getPlanId() != null) {
            paramsBuilder.putMetadata("planId", paymentRequest.getPlanId());
        }

        if (paymentRequest.getLedgerEntryId() != null) {
            paramsBuilder.putMetadata("ledgerEntryId", String.valueOf(paymentRequest.getLedgerEntryId()));
        }

        PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
        return new PaymentResponse(paymentIntent.getClientSecret());
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    @Transactional
    public Map<String, Object> processPaymentConfirmation(String paymentIntentId, String username)
            throws StripeException {
        // Idempotency check
        if (ledgerEntryRepository.existsByPaymentReference(paymentIntentId)) {
            return Map.of("message", "Payment already processed", "planUpdated", false);
        }

        PaymentIntent intent = retrievePaymentIntent(paymentIntentId);
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalArgumentException("Payment not successful");
        }

        String ledgerEntryIdStr = intent.getMetadata().get("ledgerEntryId");
        if (ledgerEntryIdStr != null) {
            Long ledgerEntryId = Long.parseLong(ledgerEntryIdStr);
            billingLedgerService.markAsPaid(ledgerEntryId, paymentIntentId);
            return Map.of("message", "Payment confirmed and ledger updated", "planUpdated", false);
        }

        String planIdStr = intent.getMetadata().get("planId");
        if (planIdStr != null) {
            UUID planId = UUID.fromString(planIdStr);
            PricingPlanVersion plan = pricingPlanVersionRepository.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

            if (plan.getPlanType() == null) {
                throw new IllegalStateException("Plan type is null for plan: " + plan.getPlanName());
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            user.setPricingPlan(plan.getPlanType());
            userRepository.save(user);

            billingLedgerService.recordPlanChange(user.getId(), plan, true, paymentIntentId);

            return Map.of("message", "Plan updated successfully to " + plan.getPlanType(), "planUpdated", true);
        }

        return Map.of("message", "Payment confirmed", "planUpdated", false);
    }
}
