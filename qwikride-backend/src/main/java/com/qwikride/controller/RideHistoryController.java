package com.qwikride.controller;

import com.qwikride.dto.RideHistoryFilterDTO;
import com.qwikride.dto.RideHistoryResponseDTO;
import com.qwikride.dto.RideStatisticsDTO;
import com.qwikride.service.RideHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Ride History operations.
 * Implements RESTful API design pattern.
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class RideHistoryController {
    private final RideHistoryService rideHistoryService;

    /**
     * Get ride history for the authenticated user.
     * Riders can only view their own ride history.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<List<RideHistoryResponseDTO>> getUserRideHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Boolean startStationOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bikeType,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        RideHistoryFilterDTO.RideHistoryFilterDTOBuilder builder = RideHistoryFilterDTO.builder()
                .page(page)
                .size(size);

        if (startDate != null && !startDate.isEmpty()) {
            try {
                builder.startDate(java.time.LocalDateTime.parse(startDate));
            } catch (Exception e) {
                // If parsing fails, try with date format YYYY-MM-DD
                try {
                    builder.startDate(java.time.LocalDate.parse(startDate).atStartOfDay());
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (endDate != null && !endDate.isEmpty()) {
            try {
                builder.endDate(java.time.LocalDateTime.parse(endDate));
            } catch (Exception e) {
                try {
                    builder.endDate(java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (stationId != null)
            builder.stationId(stationId);
        if (startStationOnly != null)
            builder.startStationOnly(startStationOnly);
        if (status != null && !status.isEmpty()) {
            try {
                builder.status(com.qwikride.model.RideHistory.RideStatus.valueOf(status));
            } catch (Exception e) {
                // Ignore invalid status
            }
        }
        if (bikeType != null && !bikeType.isEmpty())
            builder.bikeType(bikeType);
        if (tripId != null)
            builder.tripId(tripId);

        RideHistoryFilterDTO filter = builder.build();
        List<RideHistoryResponseDTO> history = rideHistoryService.getUserRideHistory(userId, filter);
        return ResponseEntity.ok(history);
    }

    /**
     * Get all ride histories (operator only).
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('OPERATOR')")
    public ResponseEntity<List<RideHistoryResponseDTO>> getAllRideHistories(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Boolean startStationOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bikeType,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        RideHistoryFilterDTO.RideHistoryFilterDTOBuilder builder = RideHistoryFilterDTO.builder()
                .userId(userId)
                .page(page)
                .size(size);

        if (startDate != null && !startDate.isEmpty()) {
            try {
                builder.startDate(java.time.LocalDateTime.parse(startDate));
            } catch (Exception e) {
                try {
                    builder.startDate(java.time.LocalDate.parse(startDate).atStartOfDay());
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (endDate != null && !endDate.isEmpty()) {
            try {
                builder.endDate(java.time.LocalDateTime.parse(endDate));
            } catch (Exception e) {
                try {
                    builder.endDate(java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (stationId != null)
            builder.stationId(stationId);
        if (startStationOnly != null)
            builder.startStationOnly(startStationOnly);
        if (status != null && !status.isEmpty()) {
            try {
                builder.status(com.qwikride.model.RideHistory.RideStatus.valueOf(status));
            } catch (Exception e) {
                // Ignore invalid status
            }
        }
        if (bikeType != null && !bikeType.isEmpty())
            builder.bikeType(bikeType);
        if (tripId != null)
            builder.tripId(tripId);

        RideHistoryFilterDTO filter = builder.build();
        List<RideHistoryResponseDTO> history = rideHistoryService.getAllRideHistories(filter);
        return ResponseEntity.ok(history);
    }

    /**
     * Get a specific ride history by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RideHistoryResponseDTO> getRideHistoryById(@PathVariable Long id) {
        return rideHistoryService.getRideHistoryById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get ride statistics for a user.
     */
    @GetMapping("/user/{userId}/statistics")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<RideStatisticsDTO> getRideStatistics(@PathVariable Long userId) {
        RideStatisticsDTO statistics = rideHistoryService.getRideStatistics(userId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get CO2 statistics grouped by year for a user.
     */
    @GetMapping("/user/{userId}/statistics/co2/year")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<List<com.qwikride.dto.Co2YearlyStatsDTO>> getCo2StatisticsByYear(@PathVariable Long userId) {
        return ResponseEntity.ok(rideHistoryService.getCo2StatisticsByYear(userId));
    }

    /**
     * Get CO2 statistics grouped by month for a specific year.
     */
    @GetMapping("/user/{userId}/statistics/co2/monthly")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<List<com.qwikride.dto.Co2MonthlyStatsDTO>> getCo2MonthlyStats(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer year) {
        int targetYear = (year != null) ? year : java.time.Year.now().getValue();
        return ResponseEntity.ok(rideHistoryService.getCo2MonthlyStats(userId, targetYear));
    }

    /**
     * Get ride history for dual-role users based on their view preference.
     * When viewAll=true: Returns all trips (operator view)
     * When viewAll=false: Returns only the user's own trips (rider view)
     */
    @GetMapping("/dual-role")
    @PreAuthorize("hasAuthority('OPERATOR')")
    public ResponseEntity<List<RideHistoryResponseDTO>> getDualRoleRideHistory(
            @RequestParam(required = true) Long userId,
            @RequestParam(required = false, defaultValue = "false") Boolean viewAll,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Boolean startStationOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bikeType,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        RideHistoryFilterDTO.RideHistoryFilterDTOBuilder builder = RideHistoryFilterDTO.builder()
                .page(page)
                .size(size);

        // If not viewing all, filter by userId
        if (!viewAll) {
            builder.userId(userId);
        }

        if (startDate != null && !startDate.isEmpty()) {
            try {
                builder.startDate(java.time.LocalDateTime.parse(startDate));
            } catch (Exception e) {
                try {
                    builder.startDate(java.time.LocalDate.parse(startDate).atStartOfDay());
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (endDate != null && !endDate.isEmpty()) {
            try {
                builder.endDate(java.time.LocalDateTime.parse(endDate));
            } catch (Exception e) {
                try {
                    builder.endDate(java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
                } catch (Exception ex) {
                    // Ignore invalid date
                }
            }
        }

        if (stationId != null)
            builder.stationId(stationId);
        if (startStationOnly != null)
            builder.startStationOnly(startStationOnly);
        if (status != null && !status.isEmpty()) {
            try {
                builder.status(com.qwikride.model.RideHistory.RideStatus.valueOf(status));
            } catch (Exception e) {
                // Ignore invalid status
            }
        }
        if (bikeType != null && !bikeType.isEmpty())
            builder.bikeType(bikeType);
        if (tripId != null)
            builder.tripId(tripId);

        RideHistoryFilterDTO filter = builder.build();
        List<RideHistoryResponseDTO> history = rideHistoryService.getAllRideHistories(filter);
        return ResponseEntity.ok(history);
    }
}
