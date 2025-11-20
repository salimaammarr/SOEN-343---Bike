package com.qwikride.prc.pricing.selector.strategy;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.pricing.domain.PricingContext;
import com.qwikride.prc.pricing.selector.PlanSelectionStrategy;
import com.qwikride.prc.pricing.selector.SelectionInput;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(10)
@RequiredArgsConstructor
public class MembershipStrategy implements PlanSelectionStrategy {
    private final PricingPlanVersionRepository repository;

    @Override
    public Optional<PricingContext> select(SelectionInput input) {
        MembershipStatus membership = input.getMembershipStatus();
        if (membership == null || membership == MembershipStatus.ENTRY) {
            return Optional.empty();
        }

        return repository.findActivePlansForMembership(membership, input.getTripEndTime()).stream()
                .findFirst()
                .map(this::toContext);
    }

    private PricingContext toContext(PricingPlanVersion version) {
        return PricingContext.builder()
                .planVersionId(version.getId())
                .rateSheet(version.toRateSheet())
                .membershipStatus(version.getMembershipTier())
                .planName(version.getPlanName())
                .build();
    }
}
