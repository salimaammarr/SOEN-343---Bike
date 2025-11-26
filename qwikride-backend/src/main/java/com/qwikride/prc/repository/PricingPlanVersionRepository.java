package com.qwikride.prc.repository;

import com.qwikride.prc.domain.PricingPlanType;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.model.PricingPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingPlanVersionRepository extends JpaRepository<PricingPlanVersion, UUID> {
        @Query("select p from PricingPlanVersion p where p.published = true and p.effectiveFrom <= :pointInTime and (p.effectiveTo is null or p.effectiveTo > :pointInTime) order by p.effectiveFrom desc")
        List<PricingPlanVersion> findActivePlans(@Param("pointInTime") LocalDateTime pointInTime);

        @Query("select p from PricingPlanVersion p where p.published = true and p.effectiveFrom <= :pointInTime and (p.effectiveTo is null or p.effectiveTo > :pointInTime) and p.membershipTier = :membership order by p.effectiveFrom desc")
        List<PricingPlanVersion> findActivePlansForMembership(@Param("membership") MembershipStatus membershipStatus,
                        @Param("pointInTime") LocalDateTime pointInTime);

        @Query("select p from PricingPlanVersion p where p.published = true and p.effectiveFrom <= :pointInTime and (p.effectiveTo is null or p.effectiveTo > :pointInTime) and p.planType = :planType order by p.effectiveFrom desc")
        List<PricingPlanVersion> findActivePlansForPlanType(@Param("planType") PricingPlanType planType,
                        @Param("pointInTime") LocalDateTime pointInTime);

        @Query("select p from PricingPlanVersion p where p.published = true and p.effectiveFrom <= :pointInTime and (p.effectiveTo is null or p.effectiveTo > :pointInTime) and p.cityId = :cityId order by p.effectiveFrom desc")
        List<PricingPlanVersion> findActivePlansForCity(@Param("cityId") String cityId,
                        @Param("pointInTime") LocalDateTime pointInTime);

        Optional<PricingPlanVersion> findFirstByPublishedTrueOrderByEffectiveFromDesc();
}
