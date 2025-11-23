package com.qwikride.controller;

import com.qwikride.model.Bike;
import com.qwikride.model.BikeStation;
import com.qwikride.model.RideHistory;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import com.qwikride.repository.RideHistoryRepository;
import com.qwikride.repository.UserRepository;
import com.qwikride.service.LoyaltyTierService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Test controller for tier progression testing.
 * Allows adding test rides to users to trigger tier transitions.
 */
@Slf4j
@RestController
@RequestMapping("/api/test/tier")
@RequiredArgsConstructor
public class TierTestController {
    private final UserRepository userRepository;
    private final RideHistoryRepository rideHistoryRepository;
    private final BikeRepository bikeRepository;
    private final BikeStationRepository bikeStationRepository;
    private final LoyaltyTierService loyaltyTierService;

    /**
     * Add a completed ride to a user for testing tier progression.
     * POST /api/test/tier/add-ride
     * Body: { "username": "entryuser", "count": 1, "daysAgo": 0 }
     */
    @PostMapping("/add-ride")
    public ResponseEntity<String> addTestRide(@RequestBody AddRideRequest request) {
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found: " + request.getUsername());
        }

        var user = userOpt.get();
        List<Bike> bikes = bikeRepository.findAll();
        List<BikeStation> stations = bikeStationRepository.findAll();

        if (bikes.isEmpty() || stations.isEmpty()) {
            return ResponseEntity.badRequest().body("No bikes or stations available");
        }

        int count = request.getCount() != null ? request.getCount() : 1;
        int daysAgo = request.getDaysAgo() != null ? request.getDaysAgo() : 0;
        int weeksAgo = request.getWeeksAgo() != null ? request.getWeeksAgo() : 0;
        int monthsAgo = request.getMonthsAgo() != null ? request.getMonthsAgo() : 0;

        LocalDateTime rideTime = LocalDateTime.now()
                .minusMonths(monthsAgo)
                .minusWeeks(weeksAgo)
                .minusDays(daysAgo);

        for (int i = 0; i < count; i++) {
            Bike bike = bikes.get(i % bikes.size());
            BikeStation startStation = stations.get(i % stations.size());
            BikeStation endStation = stations.get((i + 1) % stations.size());

            // Offset each ride by a few hours
            LocalDateTime rideStart = rideTime.minusHours(i);
            double durationMinutes = 20 + (i * 5);
            double distanceKm = 3 + (i * 0.5);
            double baseFee = (bike.getType() == com.qwikride.model.BikeType.E_BIKE) ? 3.00 : 2.00;
            double cost = baseFee + (durationMinutes * 0.25);

            RideHistory rideHistory = RideHistory.builder()
                    .userId(user.getId())
                    .bikeId(bike.getId())
                    .startStationId(startStation.getId())
                    .endStationId(endStation.getId())
                    .startTime(rideStart)
                    .endTime(rideStart.plusMinutes((long) durationMinutes))
                    .durationMinutes(durationMinutes)
                    .distanceKm(distanceKm)
                    .cost(cost)
                    .status(RideHistory.RideStatus.COMPLETED)
                    .bikeType(bike.getType() != null ? bike.getType().name() : "STANDARD")
                    .build();

            rideHistoryRepository.save(rideHistory);
        }

        // Evaluate tier after adding rides
        LoyaltyTierService.TierEvaluationResult result = loyaltyTierService.evaluateAndUpdateTier(user.getId());
        
        String message = String.format("Added %d ride(s) to user %s. Tier: %s -> %s %s",
                count, request.getUsername(), 
                result.getPreviousTier(), result.getNewTier(),
                result.isChanged() ? "(UPGRADED!)" : "");

        log.info(message);
        return ResponseEntity.ok(message);
    }

    /**
     * Get current tier status for a user.
     * GET /api/test/tier/status?username=entryuser
     */
    @GetMapping("/status")
    public ResponseEntity<TierStatusResponse> getTierStatus(@RequestParam String username) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var user = userOpt.get();
        var progress = loyaltyTierService.getTierProgress(user.getId());
        var evaluatedTier = loyaltyTierService.evaluateTier(user.getId());

        TierStatusResponse response = new TierStatusResponse();
        response.setUsername(username);
        response.setCurrentTier(user.getMembershipStatus());
        response.setEvaluatedTier(evaluatedTier);
        response.setTripsCompleted(progress.getTripsCompleted());
        response.setTripsRequired(progress.getTripsRequired());
        response.setProgressMessage(progress.getProgressMessage());
        response.setProgressPercentage(progress.getProgressPercentage());
        response.setTierChanged(!user.getMembershipStatus().equals(evaluatedTier));

        return ResponseEntity.ok(response);
    }

    /**
     * Add multiple rides spread across time periods for testing monthly/weekly requirements.
     * POST /api/test/tier/add-rides-batch
     * Body: { "username": "bronzeuser", "rides": [{ "count": 5, "weeksAgo": 1 }, { "count": 5, "weeksAgo": 2 }] }
     */
    @PostMapping("/add-rides-batch")
    public ResponseEntity<String> addTestRidesBatch(@RequestBody BatchRideRequest request) {
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found: " + request.getUsername());
        }

        var user = userOpt.get();
        List<Bike> bikes = bikeRepository.findAll();
        List<BikeStation> stations = bikeStationRepository.findAll();

        if (bikes.isEmpty() || stations.isEmpty()) {
            return ResponseEntity.badRequest().body("No bikes or stations available");
        }

        int totalRides = 0;
        for (RideBatch batch : request.getRides()) {
            LocalDateTime rideTime = LocalDateTime.now()
                    .minusMonths(batch.getMonthsAgo() != null ? batch.getMonthsAgo() : 0)
                    .minusWeeks(batch.getWeeksAgo() != null ? batch.getWeeksAgo() : 0)
                    .minusDays(batch.getDaysAgo() != null ? batch.getDaysAgo() : 0);

            for (int i = 0; i < batch.getCount(); i++) {
                Bike bike = bikes.get((totalRides + i) % bikes.size());
                BikeStation startStation = stations.get((totalRides + i) % stations.size());
                BikeStation endStation = stations.get((totalRides + i + 1) % stations.size());

                LocalDateTime rideStart = rideTime.minusHours(i);
                double durationMinutes = 20 + (i * 5);
                double distanceKm = 3 + (i * 0.5);
                double baseFee = (bike.getType() == com.qwikride.model.BikeType.E_BIKE) ? 3.00 : 2.00;
                double cost = baseFee + (durationMinutes * 0.25);

                RideHistory rideHistory = RideHistory.builder()
                        .userId(user.getId())
                        .bikeId(bike.getId())
                        .startStationId(startStation.getId())
                        .endStationId(endStation.getId())
                        .startTime(rideStart)
                        .endTime(rideStart.plusMinutes((long) durationMinutes))
                        .durationMinutes(durationMinutes)
                        .distanceKm(distanceKm)
                        .cost(cost)
                        .status(RideHistory.RideStatus.COMPLETED)
                        .bikeType(bike.getType() != null ? bike.getType().name() : "STANDARD")
                        .build();

                rideHistoryRepository.save(rideHistory);
                totalRides++;
            }
        }

        // Evaluate tier after adding rides
        LoyaltyTierService.TierEvaluationResult result = loyaltyTierService.evaluateAndUpdateTier(user.getId());
        
        String message = String.format("Added %d ride(s) to user %s. Tier: %s -> %s %s",
                totalRides, request.getUsername(), 
                result.getPreviousTier(), result.getNewTier(),
                result.isChanged() ? "(UPGRADED!)" : "");

        log.info(message);
        return ResponseEntity.ok(message);
    }

    @Data
    public static class AddRideRequest {
        private String username;
        private Integer count;
        private Integer daysAgo;
        private Integer weeksAgo;
        private Integer monthsAgo;
    }

    @Data
    public static class BatchRideRequest {
        private String username;
        private List<RideBatch> rides;
    }

    @Data
    public static class RideBatch {
        private Integer count;
        private Integer daysAgo;
        private Integer weeksAgo;
        private Integer monthsAgo;
    }

    @Data
    public static class TierStatusResponse {
        private String username;
        private com.qwikride.prc.domain.MembershipStatus currentTier;
        private com.qwikride.prc.domain.MembershipStatus evaluatedTier;
        private Integer tripsCompleted;
        private Integer tripsRequired;
        private String progressMessage;
        private Double progressPercentage;
        private Boolean tierChanged;
    }
}

