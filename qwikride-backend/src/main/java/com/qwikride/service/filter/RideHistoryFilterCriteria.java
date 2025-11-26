package com.qwikride.service.filter;

import com.qwikride.model.RideHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Criteria object for filtering ride history.
 * Uses Builder pattern for flexible query construction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideHistoryFilterCriteria {
    private Long userId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long stationId;
    private Boolean startStationOnly;
    private RideHistory.RideStatus status;
    private String bikeType;
    private UUID bikeId;
    private Long tripId;
}
