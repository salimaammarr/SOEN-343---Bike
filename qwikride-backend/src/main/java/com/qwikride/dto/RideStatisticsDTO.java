package com.qwikride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideStatisticsDTO {
    private Long totalRides;
    private Double totalDistance;
    private Double totalCost;
    private Double averageDuration;
    private Double averageDistance;
    private Long mostUsedStartStation;
    private Long mostUsedEndStation;
    private String favoriteBikeType;
    private Double totalCo2Saved;
}
