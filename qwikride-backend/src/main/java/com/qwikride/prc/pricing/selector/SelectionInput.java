package com.qwikride.prc.pricing.selector;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class SelectionInput {
    LocalDateTime tripEndTime;
    MembershipStatus membershipStatus;
    PricingPlanType pricingPlanType;
    String cityId;
}
