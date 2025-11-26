package com.qwikride.prc.model;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import com.qwikride.prc.pricing.domain.RateSheet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pricing_plan_versions")
@Getter
@Setter
@NoArgsConstructor
public class PricingPlanVersion {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String planName;

    @Column(nullable = false)
    private BigDecimal baseFee;

    @Column(nullable = false)
    private BigDecimal subscriptionPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal perMinuteRate;

    @Column
    private BigDecimal ebikeSurcharge;

    @Enumerated(EnumType.STRING)
    private MembershipStatus membershipTier;

    @Enumerated(EnumType.STRING)
    private PricingPlanType planType;

    @Column
    private String cityId;

    @Column(nullable = false)
    private LocalDateTime effectiveFrom;

    @Column
    private LocalDateTime effectiveTo;

    @Column(nullable = false)
    private boolean published;

    @Column(length = 512)
    private String description;

    public PricingPlanVersion(UUID id) {
        this.id = id;
    }

    public RateSheet toRateSheet() {
        return RateSheet.builder()
                .baseFee(baseFee)
                .perMinute(perMinuteRate)
                .ebikeSurcharge(ebikeSurcharge == null ? BigDecimal.ZERO : ebikeSurcharge)
                .build();
    }
}
