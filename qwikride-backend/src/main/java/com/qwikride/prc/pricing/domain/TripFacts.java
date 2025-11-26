package com.qwikride.prc.pricing.domain;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class TripFacts {
    UUID bikeId;
    Long riderId;
    Long startStationId;
    Long endStationId;
    LocalDateTime startTime;
    LocalDateTime endTime;
    boolean ebike;
    double distanceKm;
    MembershipStatus membershipStatus;
    PricingPlanType pricingPlanType;
    String cityId;
    boolean isOperatorActingAsRider; // True when an operator is acting as a rider

    public long durationMinutes() {
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startTime, endTime).toMinutes());
    }
}
