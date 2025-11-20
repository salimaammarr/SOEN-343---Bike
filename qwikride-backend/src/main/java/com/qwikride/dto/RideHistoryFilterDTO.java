package com.qwikride.dto;

import com.qwikride.model.RideHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideHistoryFilterDTO {
    private Long userId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long stationId;
    private Boolean startStationOnly;
    private RideHistory.RideStatus status;
    private String bikeType;
    
    @Builder.Default
    private Integer page = 0;
    
    @Builder.Default
    private Integer size = 20;
    
    @Builder.Default
    private String sortBy = "startTime";
    
    @Builder.Default
    private String sortDirection = "DESC";
}

