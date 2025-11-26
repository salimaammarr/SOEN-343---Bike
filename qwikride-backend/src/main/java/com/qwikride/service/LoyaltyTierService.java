package com.qwikride.service;

import com.qwikride.model.RideHistory;
import com.qwikride.model.User;
import com.qwikride.model.ReservationHistory;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.repository.RideHistoryRepository;
import com.qwikride.repository.UserRepository;
import com.qwikride.repository.ReservationHistoryRepository;
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
    private final ReservationHistoryRepository reservationHistoryRepository;

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
     * - Surpassed 10 trips in the last year
     * - All bikes returned (no active rides)
     * - No missed reservations (Not implemented: requires Reservation History)
     */
    private boolean meetsBronzeRequirements(Long userId, LocalDateTime oneYearAgo) {
        // Check for more than 10 trips in the last year
        long tripsLastYear = countCompletedTrips(userId, oneYearAgo, LocalDateTime.now());
        if (tripsLastYear <= 10) {
            log.debug("User {} failed Bronze: Only {} trips in last year (need >10)", userId, tripsLastYear);
            return false;
        }

        // Check that all bikes are returned (no active rides)
        // Note: We check for > 1 because during the pricing calculation (at trip end),
        // the current ride is still considered "active" in the database.
        long activeRides = rideHistoryRepository.countByUserIdAndEndTimeIsNull(userId);
        if (activeRides > 1) {
            log.debug("User {} failed Bronze: Has {} active rides (bikes not returned)", userId, activeRides);
            return false;
        }

        // Check for no missed reservations in the last year
        long missedReservations = reservationHistoryRepository.countByUserIdAndStatusAndCompletionTimeAfter(
                userId, ReservationHistory.ReservationStatus.EXPIRED, oneYearAgo);
        if (missedReservations > 0) {
            log.debug("User {} failed Bronze: Has {} missed reservations in last year", userId, missedReservations);
            return false;
        }

        return true;
    }

    /**
     * Check Silver tier requirements:
     * Must meet Bronze requirements + 5 trips per month for the last three months
     * - > 5 claimed reservations (Not implemented: requires Reservation History)
     */
    private boolean meetsSilverRequirements(Long userId, LocalDateTime oneYearAgo, LocalDateTime threeMonthsAgo) {
        // Must meet Bronze requirements first
        if (!meetsBronzeRequirements(userId, oneYearAgo)) {
            return false;
        }

        // Check for > 5 claimed reservations in the last year
        long claimedReservations = reservationHistoryRepository.countByUserIdAndStatusAndCompletionTimeAfter(
                userId, ReservationHistory.ReservationStatus.CLAIMED, oneYearAgo);
        if (claimedReservations <= 5) {
            log.debug("User {} failed Silver: Only {} claimed reservations in last year (need >5)", userId,
                    claimedReservations);
            return false;
        }

        // Check for > 5 trips per month for the last 3 months
        boolean meetsMonthlyRequirement = checkMonthlyTripRequirement(userId, threeMonthsAgo, 5);
        if (!meetsMonthlyRequirement) {
            log.debug("User {} failed Silver: Did not meet 5 trips/month for last 3 months", userId);
            return false;
        }

        return true;
    }

    /**
     * Check Gold tier requirements:
     * Must meet Silver requirements + 5 trips every week for the last 12 weeks
     */
    private boolean meetsGoldRequirements(Long userId, LocalDateTime threeMonthsAgo) {
        // Must meet Silver requirements first
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        if (!meetsSilverRequirements(userId, oneYearAgo, threeMonthsAgo)) {
            return false;
        }

        // Surpasses 5 trips every week for the last 12 weeks
        boolean meetsWeeklyRequirement = checkWeeklyTripRequirement(userId, threeMonthsAgo, 5);
        if (!meetsWeeklyRequirement) {
            log.debug("User {} failed Gold: Did not meet 5 trips/week for last 12 weeks", userId);
            return false;
        }

        return true;
    }

    /**
     * Count completed trips in a time period
     */
    private long countCompletedTrips(Long userId, LocalDateTime start, LocalDateTime end) {
        List<RideHistory> rides = rideHistoryRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
                userId, start, end);

        return rides.stream()
                .filter(ride -> ride.getStatus() == RideHistory.RideStatus.COMPLETED)
                .count();
    }

    /**
     * Check if rider has at least minTrips per month for the last 3 months
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
     * Check if rider has at least minTrips per week for the last 12 weeks
     */
    private boolean checkWeeklyTripRequirement(Long userId, LocalDateTime threeMonthsAgo, int minTrips) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twelveWeeksAgo = LocalDateTime.now().minusWeeks(12);

        // Check each of the last 12 weeks (same pattern as monthly check)
        for (int i = 0; i < 12; i++) {
            LocalDateTime weekStart = twelveWeeksAgo.plusWeeks(i);
            LocalDateTime weekEnd = weekStart.plusWeeks(1);
            if (weekEnd.isAfter(now)) {
                weekEnd = now;
            }

            long tripsInWeek = countCompletedTrips(userId, weekStart, weekEnd);
            if (tripsInWeek < minTrips) {
                return false;
            }
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

    /**
     * Get tier progress information for a user.
     */
    public com.qwikride.dto.TierProgressDTO getTierProgress(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MembershipStatus currentTier = user.getMembershipStatus();
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        // Count trips in last year
        long tripsCompleted = countCompletedTrips(userId, oneYearAgo, LocalDateTime.now());

        // Check for missed reservations
        long missedReservations = reservationHistoryRepository.countByUserIdAndStatusAndCompletionTimeAfter(
                userId, ReservationHistory.ReservationStatus.EXPIRED, oneYearAgo);
        boolean hasMissedReservations = missedReservations > 0;

        // Check for successful reservations
        long successfulReservations = reservationHistoryRepository.countByUserIdAndStatusAndCompletionTimeAfter(
                userId, ReservationHistory.ReservationStatus.CLAIMED, oneYearAgo);

        MembershipStatus nextTier = getNextTier(currentTier);
        String progressMessage = "";
        double progressPercentage = 0.0;
        int tripsRequired = 0;
        int reservationsRequired = 0;

        if (nextTier == null) {
            // Already at max tier
            progressMessage = "You've reached the highest tier!";
            progressPercentage = 100.0;
        } else {
            switch (nextTier) {
                case ENTRY:
                    // Should never happen, but handle for completeness
                    progressMessage = "Invalid tier state";
                    progressPercentage = 0.0;
                    break;
                case BRONZE:
                    tripsRequired = 10;
                    if (hasMissedReservations) {
                        progressMessage = "Cannot upgrade: You have missed reservations in the last year.";
                        progressPercentage = 0.0;
                    } else {
                        int progress = (int) Math.min(100, (tripsCompleted * 100.0 / tripsRequired));
                        progressPercentage = progress;
                        progressMessage = String.format("%d / %d trips completed", tripsCompleted, tripsRequired);
                    }
                    break;
                case SILVER:
                    reservationsRequired = 5;
                    if (!meetsBronzeRequirements(userId, oneYearAgo)) {
                        progressMessage = "Meet Bronze tier requirements first";
                        progressPercentage = 0.0;
                    } else if (successfulReservations <= reservationsRequired) {
                        progressMessage = String.format("Need more claimed reservations (%d/%d)",
                                successfulReservations, reservationsRequired);
                        progressPercentage = (successfulReservations * 100.0 / reservationsRequired);
                    } else {
                        // Check monthly requirement
                        boolean meetsMonthly = checkMonthlyTripRequirement(userId, threeMonthsAgo, 5);
                        if (!meetsMonthly) {
                            progressMessage = "Complete 5 trips per month for the last 3 months";
                            progressPercentage = 50.0;
                        } else {
                            progressMessage = "Almost there! Keep up the good work.";
                            progressPercentage = 90.0;
                        }
                    }
                    break;
                case GOLD:
                    if (!meetsSilverRequirements(userId, oneYearAgo, threeMonthsAgo)) {
                        progressMessage = "Meet Silver tier requirements first";
                        progressPercentage = 0.0;
                    } else {
                        // Check weekly requirement
                        boolean meetsWeekly = checkWeeklyTripRequirement(userId, threeMonthsAgo, 5);
                        if (meetsWeekly) {
                            progressMessage = "You're eligible for Gold tier!";
                            progressPercentage = 100.0;
                        } else {
                            progressMessage = "Complete 5 trips per week for the last 12 weeks";
                            progressPercentage = 75.0;
                        }
                    }
                    break;
            }
        }

        return com.qwikride.dto.TierProgressDTO.builder()
                .currentTier(currentTier)
                .nextTier(nextTier)
                .tripsCompleted((int) tripsCompleted)
                .tripsRequired(tripsRequired)
                .hasMissedReservations(hasMissedReservations)
                .hasCancelledRides(false) // Not tracking cancelled rides for now
                .successfulReservations((int) successfulReservations)
                .reservationsRequired(reservationsRequired)
                .progressMessage(progressMessage)
                .progressPercentage(progressPercentage)
                .build();
    }

    private MembershipStatus getNextTier(MembershipStatus current) {
        return switch (current) {
            case ENTRY -> MembershipStatus.BRONZE;
            case BRONZE -> MembershipStatus.SILVER;
            case SILVER -> MembershipStatus.GOLD;
            case GOLD -> null; // Max tier
            default -> null;
        };
    }
}
