package com.qwikride.dto;

import com.qwikride.prc.domain.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierProgressDTO {
    private MembershipStatus currentTier;
    private MembershipStatus nextTier;
    private int tripsCompleted; // Trips in last year
    private int tripsRequired; // Trips needed for next tier
    private boolean hasMissedReservations; // BR-001
    private boolean hasCancelledRides; // BR-002
    private int successfulReservations; // SL-002
    private int reservationsRequired; // SL-002 requirement
    private String progressMessage;
    private double progressPercentage; // 0-100
}

