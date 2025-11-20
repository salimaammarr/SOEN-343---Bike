package com.qwikride.service;

import com.qwikride.dto.RideHistoryFilterDTO;
import com.qwikride.dto.RideHistoryResponseDTO;
import com.qwikride.dto.RideStatisticsDTO;
import com.qwikride.model.RideHistory;
import com.qwikride.repository.RideHistoryRepository;
import com.qwikride.service.filter.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing ride history.
 * Uses Strategy Pattern for filtering and Repository Pattern for data access.
 */
@Service
@RequiredArgsConstructor
public class RideHistoryService {
    private final RideHistoryRepository rideHistoryRepository;
    private final List<RideHistoryFilterStrategy> filterStrategies;

    /**
     * Get ride history for a specific user with optional filters.
     * Uses Strategy Pattern to apply multiple filters.
     */
    public List<RideHistoryResponseDTO> getUserRideHistory(Long userId, RideHistoryFilterDTO filter) {
        RideHistoryFilterCriteria criteria = buildFilterCriteria(userId, filter);
        Specification<RideHistory> spec = buildSpecification(criteria);

        Pageable pageable = createPageable(filter);
        Page<RideHistory> historyPage = rideHistoryRepository.findAll(spec, pageable);

        return historyPage.getContent().stream()
                .map(RideHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all ride histories (for operators) with optional filters.
     */
    public List<RideHistoryResponseDTO> getAllRideHistories(RideHistoryFilterDTO filter) {
        RideHistoryFilterCriteria criteria = buildFilterCriteria(null, filter);
        Specification<RideHistory> spec = buildSpecification(criteria);

        Pageable pageable = createPageable(filter);
        Page<RideHistory> historyPage = rideHistoryRepository.findAll(spec, pageable);

        return historyPage.getContent().stream()
                .map(RideHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific ride history by ID.
     */
    public Optional<RideHistoryResponseDTO> getRideHistoryById(Long id) {
        return rideHistoryRepository.findById(id)
                .map(RideHistoryResponseDTO::fromEntity);
    }

    /**
     * Get ride statistics for a user.
     * Uses aggregation to calculate statistics.
     */
    public RideStatisticsDTO getRideStatistics(Long userId) {
        List<RideHistory> rides = rideHistoryRepository.findByUserIdOrderByStartTimeDesc(userId);

        if (rides.isEmpty()) {
            return RideStatisticsDTO.builder()
                    .totalRides(0L)
                    .totalDistance(0.0)
                    .totalCost(0.0)
                    .averageDuration(0.0)
                    .averageDistance(0.0)
                    .build();
        }

        long totalRides = rides.size();
        double totalDistance = rides.stream()
                .filter(r -> r.getDistanceKm() != null)
                .mapToDouble(RideHistory::getDistanceKm)
                .sum();
        double totalCost = rides.stream()
                .filter(r -> r.getCost() != null)
                .mapToDouble(RideHistory::getCost)
                .sum();
        double avgDuration = rides.stream()
                .filter(r -> r.getDurationMinutes() != null)
                .mapToDouble(RideHistory::getDurationMinutes)
                .average()
                .orElse(0.0);
        double avgDistance = totalDistance / totalRides;

        // Find most used stations
        Long mostUsedStartStation = rides.stream()
                .filter(r -> r.getStartStationId() != null)
                .collect(Collectors.groupingBy(RideHistory::getStartStationId, Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);

        Long mostUsedEndStation = rides.stream()
                .filter(r -> r.getEndStationId() != null)
                .collect(Collectors.groupingBy(RideHistory::getEndStationId, Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);

        // Find favorite bike type
        String favoriteBikeType = rides.stream()
                .filter(r -> r.getBikeType() != null)
                .collect(Collectors.groupingBy(RideHistory::getBikeType, Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);

        return RideStatisticsDTO.builder()
                .totalRides(totalRides)
                .totalDistance(totalDistance)
                .totalCost(totalCost)
                .averageDuration(avgDuration)
                .averageDistance(avgDistance)
                .mostUsedStartStation(mostUsedStartStation)
                .mostUsedEndStation(mostUsedEndStation)
                .favoriteBikeType(favoriteBikeType)
                .build();
    }

    /**
     * Create a new ride history entry (used when trip starts).
     */
    @Transactional
    public RideHistory createRideHistory(UUID bikeId, Long userId, Long startStationId, String bikeType) {
        RideHistory rideHistory = RideHistory.builder()
                .bikeId(bikeId)
                .userId(userId)
                .startStationId(startStationId)
                .startTime(LocalDateTime.now())
                .status(RideHistory.RideStatus.IN_PROGRESS)
                .bikeType(bikeType)
                .build();

        return rideHistoryRepository.save(rideHistory);
    }

    /**
     * Update ride history when trip ends.
     */
    @Transactional
    public RideHistory updateRideHistory(Long rideHistoryId, Long endStationId,
            Double durationMinutes, Double distanceKm, Double cost) {
        RideHistory rideHistory = rideHistoryRepository.findById(rideHistoryId)
                .orElseThrow(() -> new IllegalArgumentException("Ride history not found"));

        rideHistory.setEndStationId(endStationId);
        rideHistory.setEndTime(LocalDateTime.now());
        rideHistory.setDurationMinutes(durationMinutes);
        rideHistory.setDistanceKm(distanceKm);
        rideHistory.setCost(cost);
        rideHistory.setStatus(RideHistory.RideStatus.COMPLETED);

        return rideHistoryRepository.save(rideHistory);
    }

    /**
     * Find in-progress ride for a user.
     */
    public Optional<RideHistory> findInProgressRide(Long userId) {
        return rideHistoryRepository.findFirstByUserIdAndStatusOrderByStartTimeDesc(userId,
                RideHistory.RideStatus.IN_PROGRESS);
    }

    /**
     * Build filter criteria from DTO.
     * Uses Builder Pattern.
     */
    private RideHistoryFilterCriteria buildFilterCriteria(Long userId, RideHistoryFilterDTO filter) {
        // Use userId from filter if provided, otherwise use the parameter
        Long effectiveUserId = (filter != null && filter.getUserId() != null) ? filter.getUserId() : userId;
        
        RideHistoryFilterCriteria.RideHistoryFilterCriteriaBuilder builder = RideHistoryFilterCriteria.builder()
                .userId(effectiveUserId);

        if (filter != null) {
            if (filter.getStartDate() != null) {
                builder.startDate(filter.getStartDate());
            }
            if (filter.getEndDate() != null) {
                builder.endDate(filter.getEndDate());
            }
            if (filter.getStationId() != null) {
                builder.stationId(filter.getStationId());
            }
            if (filter.getStartStationOnly() != null) {
                builder.startStationOnly(filter.getStartStationOnly());
            }
            if (filter.getStatus() != null) {
                builder.status(filter.getStatus());
            }
            if (filter.getBikeType() != null) {
                builder.bikeType(filter.getBikeType());
            }
        }

        return builder.build();
    }

    /**
     * Build JPA Specification by combining all filter strategies.
     * Uses Strategy Pattern to combine multiple filters.
     */
    private Specification<RideHistory> buildSpecification(RideHistoryFilterCriteria criteria) {
        Specification<RideHistory> spec = Specification.where(null);

        // Apply user filter
        if (criteria.getUserId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), criteria.getUserId()));
        }

        // Apply all filter strategies
        for (RideHistoryFilterStrategy strategy : filterStrategies) {
            Specification<RideHistory> strategySpec = strategy.buildSpecification(criteria);
            if (strategySpec != null) {
                spec = spec.and(strategySpec);
            }
        }

        return spec;
    }

    /**
     * Create Pageable object from filter DTO.
     */
    private Pageable createPageable(RideHistoryFilterDTO filter) {
        int page = (filter != null && filter.getPage() != null) ? filter.getPage() : 0;
        int size = (filter != null && filter.getSize() != null) ? filter.getSize() : 20;
        String sortBy = (filter != null && filter.getSortBy() != null) ? filter.getSortBy() : "startTime";
        Sort.Direction direction = (filter != null && "ASC".equalsIgnoreCase(filter.getSortDirection()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
