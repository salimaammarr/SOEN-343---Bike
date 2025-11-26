package com.qwikride.prc.service;

import com.qwikride.prc.dto.ExampleCostResponse;
import com.qwikride.prc.dto.PricingPlanResponse;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingPlanService {
    private final PricingPlanVersionRepository repository;

    @Transactional(readOnly = true)
    public List<PricingPlanResponse> listPublishedPlans() {
        return repository.findAll().stream()
                .filter(PricingPlanVersion::isPublished)
                .sorted((a, b) -> b.getEffectiveFrom().compareTo(a.getEffectiveFrom()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PricingPlanVersion getPlan(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pricing plan not found"));
    }

    public PricingPlanResponse toResponse(PricingPlanVersion version) {
        return new PricingPlanResponse(
                version.getId(),
                version.getPlanName(),
                version.getBaseFee(),
                version.getPerMinuteRate(),
                version.getEbikeSurcharge(),
                version.getSubscriptionPrice(),
                version.getMembershipTier(),
                version.getPlanType(),
                version.getEffectiveFrom(),
                version.getEffectiveTo(),
                version.getDescription(),
                buildExamples(version));
    }

    private List<ExampleCostResponse> buildExamples(PricingPlanVersion version) {
        return List.of(
                new ExampleCostResponse(15, false, estimate(version, 15, false)),
                new ExampleCostResponse(30, false, estimate(version, 30, false)),
                new ExampleCostResponse(30, true, estimate(version, 30, true)));
    }

    private BigDecimal estimate(PricingPlanVersion version, int minutes, boolean ebike) {
        BigDecimal total = version.getBaseFee().add(version.getPerMinuteRate().multiply(BigDecimal.valueOf(minutes)));
        if (ebike && version.getEbikeSurcharge() != null) {
            total = total.add(version.getEbikeSurcharge());
        }
        return total;
    }

}
