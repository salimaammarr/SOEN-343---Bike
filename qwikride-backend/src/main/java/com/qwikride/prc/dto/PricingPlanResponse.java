package com.qwikride.prc.dto;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PricingPlanResponse(
                UUID planVersionId,
                String planName,
                BigDecimal baseFee,
                BigDecimal perMinuteRate,
                BigDecimal ebikeSurcharge,
                BigDecimal subscriptionPrice,
                MembershipStatus membershipTier,
                PricingPlanType planType,
                LocalDateTime effectiveFrom,
                LocalDateTime effectiveTo,
                String description,
                List<ExampleCostResponse> exampleCosts) {
}