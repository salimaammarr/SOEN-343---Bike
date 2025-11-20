package com.qwikride.prc.pricing.rule;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.pricing.domain.ChargeLine;
import com.qwikride.prc.pricing.domain.MutableBill;
import com.qwikride.prc.pricing.domain.PricingContext;
import com.qwikride.prc.pricing.domain.TripFacts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Applies loyalty tier discounts to the bill.
 * Bronze: 5% discount
 * Silver: 10% discount
 * Gold: 15% discount
 */
@Component
public class DiscountHandler extends AbstractRuleHandler {
    @Override
    protected void doApply(PricingContext context, TripFacts tripFacts, MutableBill bill) {
        MembershipStatus tier = tripFacts.getMembershipStatus();
        if (tier == null || tier == MembershipStatus.ENTRY) {
            return; // No discount for ENTRY tier
        }

        BigDecimal discountPercentage = getDiscountPercentage(tier);
        if (discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Calculate discount on the current total
        BigDecimal currentTotal = bill.total();
        BigDecimal discountAmount = currentTotal
                .multiply(discountPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .negate(); // Negative amount for discount

        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            ChargeLine discountLine = ChargeLine.builder()
                    .code("LOYALTY_DISCOUNT")
                    .amount(discountAmount)
                    .meta(Map.of(
                            "tier", tier.name(),
                            "discountPercentage", discountPercentage.toString()
                    ))
                    .build();
            bill.add(discountLine);
        }
    }

    private BigDecimal getDiscountPercentage(MembershipStatus tier) {
        return switch (tier) {
            case BRONZE -> BigDecimal.valueOf(5.0);
            case SILVER -> BigDecimal.valueOf(10.0);
            case GOLD -> BigDecimal.valueOf(15.0);
            case ENTRY -> BigDecimal.ZERO;
        };
    }
}
