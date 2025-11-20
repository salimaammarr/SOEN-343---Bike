package com.qwikride.prc.service;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.service.LoyaltyTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipService {
    private final LoyaltyTierService loyaltyTierService;

    /**
     * Resolve the user's membership tier, evaluating and updating it if necessary.
     * This ensures the tier is always accurate based on current usage data.
     */
    @Transactional
    public MembershipStatus resolveMembership(Long userId) {
        // Evaluate and update tier based on current usage data
        // This ensures billing uses the correct tier
        LoyaltyTierService.TierEvaluationResult result = loyaltyTierService.evaluateAndUpdateTier(userId);
        
        // Return the evaluated tier (which may have been updated)
        return result.getNewTier();
    }
}
