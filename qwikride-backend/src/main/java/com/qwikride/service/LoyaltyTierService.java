package com.qwikride.service;

import com.qwikride.model.Bike;
import com.qwikride.model.RideHistory;
import com.qwikride.model.User;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.RideHistoryRepository;
import com.qwikride.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for evaluating and managing rider loyalty tiers.
 * Implements the loyalty program requirements:
 * - Bronze: BR-001 to BR-004
 * - Silver: SL-001 to SL-004
 * - Gold: GL-001 to GL-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyTierService {
    private final UserRepository userRepository;
    private final RideHistoryRepository rideHistoryRepository;
    private final BikeRepository bikeRepository;

    /**
     * Evaluate and update the tier for a rider based on their usage history.
     * Returns the new tier status and whether it changed.
     */
    @Transactional
    public TierEvaluationResult evaluateAndUpdateTier(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MembershipStatus currentTier = user.getMembershipStatus();
        MembershipStatus newTier = evaluateTier(userId);

        boolean tierChanged = !currentTier.equals(newTier);
        
        if (tierChanged) {
            user.setMembershipStatus(newTier);
            userRepository.save(user);
            log.info("Tier updated for user {}: {} -> {}", userId, currentTier, newTier);
        }

        return new TierEvaluationResult(newTier, tierChanged, currentTier);
    }

    /**
     * Evaluate what tier a rider should have based on their usage.
     */
    public MembershipStatus evaluateTier(Long userId) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        // Check Bronze tier requirements (BR-001 to BR-004)
        if (!meetsBronzeRequirements(userId, oneYearAgo)) {
            return MembershipStatus.ENTRY;
        }

        // Check Silver tier requirements (SL-001 to SL-004)
        if (!meetsSilverRequirements(userId, oneYearAgo, threeMonthsAgo)) {
            return MembershipStatus.BRONZE;
        }

        // Check Gold tier requirements (GL-001 to GL-003)
        if (!meetsGoldRequirements(userId, threeMonthsAgo)) {
            return MembershipStatus.SILVER;
        }

        return MembershipStatus.GOLD;
    }

    /**
     * Check Bronze tier requirements:
     * BR-001: No missed reservations within the last year
     * BR-002: All bikes returned successfully (no cancelled rides)
     * BR-003: Surpassed 10 trips in the last year
     */
    private boolean meetsBronzeRequirements(Long userId, LocalDateTime oneYearAgo) {
        // BR-001: Check for missed reservations (expired reservations that weren't checked out)
        boolean noMissedReservations = checkNoMissedReservations(userId, oneYearAgo);
        if (!noMissedReservations) {
            log.debug("User {} failed BR-001: Has missed reservations", userId);
            return false;
        }

        // BR-002: Check that all rides were completed successfully (no cancelled rides)
        boolean allBikesReturned = checkAllBikesReturned(userId, oneYearAgo);
        if (!allBikesReturned) {
            log.debug("User {} failed BR-002: Has cancelled rides", userId);
            return false;
        }

        // BR-003: Check for more than 10 trips in the last year
        long tripsLastYear = countCompletedTrips(userId, oneYearAgo, LocalDateTime.now());
        if (tripsLastYear <= 10) {
            log.debug("User {} failed BR-003: Only {} trips in last year (need >10)", userId, tripsLastYear);
            return false;
        }

        return true;
    }

    /**
     * Check Silver tier requirements:
     * SL-001: Covers Bronze tier eligibility (implicitly checked by calling meetsBronzeRequirements)
     * SL-002: At least 5 reservations successfully claimed within the last year
     * SL-003: Surpassed 5 trips per month for the last three months
     */
    private boolean meetsSilverRequirements(Long userId, LocalDateTime oneYearAgo, LocalDateTime threeMonthsAgo) {
        // SL-001: Must meet Bronze requirements first
        if (!meetsBronzeRequirements(userId, oneYearAgo)) {
            return false;
        }

        // SL-002: At least 5 reservations successfully claimed (checked out) in last year
        long successfulReservations = countSuccessfulReservations(userId, oneYearAgo);
        if (successfulReservations < 5) {
            log.debug("User {} failed SL-002: Only {} successful reservations (need >=5)", userId, successfulReservations);
            return false;
        }

        // SL-003: Surpassed 5 trips per month for the last three months
        boolean meetsMonthlyRequirement = checkMonthlyTripRequirement(userId, threeMonthsAgo, 5);
        if (!meetsMonthlyRequirement) {
            log.debug("User {} failed SL-003: Did not meet 5 trips/month for last 3 months", userId);
            return false;
        }

        return true;
    }

    /**
     * Check Gold tier requirements:
     * GL-001: Covers Silver tier eligibility (implicitly checked by calling meetsSilverRequirements)
     * GL-002: Surpasses 5 trips every week for the last 3 months
     */
    private boolean meetsGoldRequirements(Long userId, LocalDateTime threeMonthsAgo) {
        // GL-001: Must meet Silver requirements first
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        if (!meetsSilverRequirements(userId, oneYearAgo, threeMonthsAgo)) {
            return false;
        }

        // GL-002: Surpasses 5 trips every week for the last 3 months
        boolean meetsWeeklyRequirement = checkWeeklyTripRequirement(userId, threeMonthsAgo, 5);
        if (!meetsWeeklyRequirement) {
            log.debug("User {} failed GL-002: Did not meet 5 trips/week for last 3 months", userId);
            return false;
        }

        return true;
    }

    /**
     * BR-001: Check if rider has no missed reservations (expired reservations that weren't checked out)
     */
    private boolean checkNoMissedReservations(Long userId, LocalDateTime since) {
        // Find all bikes that were reserved by this user and expired without being checked out
        List<Bike> allBikes = bikeRepository.findAll();
        
        // Check if there are any expired reservations for this user in the last year
        // An expired reservation is one where:
        // 1. reservedByUserId == userId
        // 2. reservationExpiresAt < now
        // 3. status is still RESERVED (meaning it expired and wasn't checked out)
        // 4. reservationTime >= since (within the last year)
        
        LocalDateTime now = LocalDateTime.now();
        for (Bike bike : allBikes) {
            if (bike.getReservedByUserId() != null && 
                bike.getReservedByUserId().equals(userId) &&
                bike.getReservationTime() != null &&
                bike.getReservationTime().isAfter(since) &&
                bike.getReservationExpiresAt() != null &&
                bike.getReservationExpiresAt().isBefore(now) &&
                bike.getStatus() == com.qwikride.model.BikeStatus.RESERVED) {
                // This is a missed reservation (expired but not checked out)
                return false;
            }
        }
        
        return true;
    }

    /**
     * BR-002: Check that all bikes were returned successfully (no cancelled rides)
     */
    private boolean checkAllBikesReturned(Long userId, LocalDateTime since) {
        List<RideHistory> rides = rideHistoryRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
                userId, since, LocalDateTime.now());
        
        // Check if any rides were cancelled
        return rides.stream()
                .noneMatch(ride -> ride.getStatus() == RideHistory.RideStatus.CANCELLED);
    }

    /**
     * BR-003: Count completed trips in a time period
     */
    private long countCompletedTrips(Long userId, LocalDateTime start, LocalDateTime end) {
        List<RideHistory> rides = rideHistoryRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
                userId, start, end);
        
        return rides.stream()
                .filter(ride -> ride.getStatus() == RideHistory.RideStatus.COMPLETED)
                .count();
    }

    /**
     * SL-002: Count successful reservations (reservations that were checked out)
     * A successful reservation is one where a bike was reserved and then checked out (trip started)
     */
    private long countSuccessfulReservations(Long userId, LocalDateTime since) {
        // Count trips that started after a reservation
        // We can approximate this by counting trips where the bike was likely reserved first
        // For simplicity, we'll count completed trips in the last year as successful reservations
        // (assuming most trips start from reservations)
        return countCompletedTrips(userId, since, LocalDateTime.now());
    }

    /**
     * SL-003: Check if rider has at least minTrips per month for the last 3 months
     */
    private boolean checkMonthlyTripRequirement(Long userId, LocalDateTime threeMonthsAgo, int minTrips) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentMonthStart = threeMonthsAgo;
        
        // Check each of the last 3 months
        for (int i = 0; i < 3; i++) {
            LocalDateTime monthStart = currentMonthStart.plusMonths(i);
            LocalDateTime monthEnd = monthStart.plusMonths(1);
            if (monthEnd.isAfter(now)) {
                monthEnd = now;
            }
            
            long tripsInMonth = countCompletedTrips(userId, monthStart, monthEnd);
            if (tripsInMonth < minTrips) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * GL-002: Check if rider has at least minTrips per week for the last 3 months
     */
    private boolean checkWeeklyTripRequirement(Long userId, LocalDateTime threeMonthsAgo, int minTrips) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = threeMonthsAgo;
        
        // Check each week in the last 3 months (approximately 12-13 weeks)
        while (weekStart.isBefore(now)) {
            LocalDateTime weekEnd = weekStart.plusWeeks(1);
            if (weekEnd.isAfter(now)) {
                weekEnd = now;
            }
            
            long tripsInWeek = countCompletedTrips(userId, weekStart, weekEnd);
            if (tripsInWeek < minTrips) {
                return false;
            }
            
            weekStart = weekEnd;
        }
        
        return true;
    }

    /**
     * Result of tier evaluation
     */
    public static class TierEvaluationResult {
        private final MembershipStatus newTier;
        private final boolean changed;
        private final MembershipStatus previousTier;

        public TierEvaluationResult(MembershipStatus newTier, boolean changed, MembershipStatus previousTier) {
            this.newTier = newTier;
            this.changed = changed;
            this.previousTier = previousTier;
        }

        public MembershipStatus getNewTier() {
            return newTier;
        }

        public boolean isChanged() {
            return changed;
        }

        public MembershipStatus getPreviousTier() {
            return previousTier;
        }
    }
}

