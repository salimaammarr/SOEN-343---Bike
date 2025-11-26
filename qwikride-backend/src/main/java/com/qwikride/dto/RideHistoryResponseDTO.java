package com.qwikride.dto;

import com.qwikride.model.RideHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideHistoryResponseDTO {
    private Long id;
    private Long userId;
    private UUID bikeId;
    private Long startStationId;
    private Long endStationId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double durationMinutes;
    private Double distanceKm;
    private Double cost;
    private RideHistory.RideStatus status;
    private String bikeType;
    private Double co2Saved; // in kg

    public static RideHistoryResponseDTO fromEntity(RideHistory rideHistory) {
        double distance = rideHistory.getDistanceKm() != null ? rideHistory.getDistanceKm() : 0.0;
        // Calculate CO2 saved:
        // Standard car emits ~150g/km
        // E-bike emits ~15g/km (net savings 135g/km)
        // Standard bike emits 0g/km (net savings 150g/km)
        double savingsPerKm = "E_BIKE".equalsIgnoreCase(rideHistory.getBikeType()) ? 0.135 : 0.150;
        double co2Saved = distance * savingsPerKm;

        return RideHistoryResponseDTO.builder()
                .id(rideHistory.getId())
                .userId(rideHistory.getUserId())
                .bikeId(rideHistory.getBikeId())
                .startStationId(rideHistory.getStartStationId())
                .endStationId(rideHistory.getEndStationId())
                .startTime(rideHistory.getStartTime())
                .endTime(rideHistory.getEndTime())
                .durationMinutes(rideHistory.getDurationMinutes())
                .distanceKm(rideHistory.getDistanceKm())
                .cost(rideHistory.getCost())
                .status(rideHistory.getStatus())
                .bikeType(rideHistory.getBikeType())
                .co2Saved(co2Saved)
                .build();
    }
}
