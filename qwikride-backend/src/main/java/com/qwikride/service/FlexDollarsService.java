package com.qwikride.service;

import com.qwikride.model.BikeStation;
import com.qwikride.model.User;
import com.qwikride.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for managing flex dollars credits.
 * Flex dollars are awarded for returning bikes to stations below 25% capacity.
 * They can be used for trips and reservations and don't expire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlexDollarsService {
    private final UserRepository userRepository;
    
    // Amount of flex dollars awarded per return to low-occupancy station
    private static final BigDecimal FLEX_DOLLARS_REWARD = BigDecimal.valueOf(2.00);
    
    // Amount of flex dollars awarded for returning to a full station (overflow)
    private static final BigDecimal OVERFLOW_CREDIT_REWARD = BigDecimal.valueOf(1.00);
    
    // Minimum capacity threshold (25%)
    private static final double MINIMUM_CAPACITY_THRESHOLD = 0.25;

    /**
     * Award flex dollars for returning to a full station (overflow).
     * @param station The station where the bike was returned
     * @param userId The user who returned the bike
     * @return The amount of flex dollars awarded
     */
    @Transactional
    public BigDecimal awardOverflowCredit(BikeStation station, Long userId) {
        if (station == null || userId == null) {
            return BigDecimal.ZERO;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        BigDecimal currentBalance = user.getFlexDollars() != null 
                ? user.getFlexDollars() 
                : BigDecimal.ZERO;
        
        BigDecimal newBalance = currentBalance.add(OVERFLOW_CREDIT_REWARD);
        user.setFlexDollars(newBalance);
        userRepository.save(user);

        log.info("Awarded {} flex dollars to user {} for returning bike to full station {} (Overflow)", 
                OVERFLOW_CREDIT_REWARD, userId, station.getName());

        return OVERFLOW_CREDIT_REWARD;
    }

    /**
     * Check if a station is below minimum capacity (< 25%) and award flex dollars if so.
     * @param station The station where the bike was returned
     * @param userId The user who returned the bike
     * @return The amount of flex dollars awarded (0 if station not below threshold)
     */
    @Transactional
    public BigDecimal awardFlexDollarsIfEligible(BikeStation station, Long userId) {
        if (station == null || userId == null) {
            return BigDecimal.ZERO;
        }

        // Check if station is below 25% capacity
        if (isBelowMinimumCapacity(station)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            BigDecimal currentBalance = user.getFlexDollars() != null 
                    ? user.getFlexDollars() 
                    : BigDecimal.ZERO;
            
            BigDecimal newBalance = currentBalance.add(FLEX_DOLLARS_REWARD);
            user.setFlexDollars(newBalance);
            userRepository.save(user);

            log.info("Awarded {} flex dollars to user {} for returning bike to low-occupancy station {} ({}% full)", 
                    FLEX_DOLLARS_REWARD, userId, station.getName(), 
                    calculateOccupancyPercentage(station));

            return FLEX_DOLLARS_REWARD;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Use flex dollars to pay for a trip or reservation.
     * @param userId The user making the payment
     * @param amount The amount to pay
     * @return The amount actually paid using flex dollars (may be less than requested if balance is insufficient)
     */
    @Transactional
    public BigDecimal useFlexDollars(Long userId, BigDecimal amount) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        BigDecimal currentBalance = user.getFlexDollars() != null 
                ? user.getFlexDollars() 
                : BigDecimal.ZERO;

        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Use as much flex dollars as available, up to the requested amount
        BigDecimal amountToUse = currentBalance.min(amount);
        BigDecimal newBalance = currentBalance.subtract(amountToUse);
        
        user.setFlexDollars(newBalance);
        userRepository.save(user);

        log.info("Used {} flex dollars for user {} (remaining balance: {})", 
                amountToUse, userId, newBalance);

        return amountToUse;
    }

    /**
     * Get the current flex dollars balance for a user.
     */
    public BigDecimal getBalance(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        return userRepository.findById(userId)
                .map(user -> user.getFlexDollars() != null ? user.getFlexDollars() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Check if a station is below minimum capacity (< 25%).
     */
    private boolean isBelowMinimumCapacity(BikeStation station) {
        if (station.getCapacity() == null || station.getCapacity() == 0) {
            return false;
        }

        double occupancyPercentage = calculateOccupancyPercentage(station);
        return occupancyPercentage < (MINIMUM_CAPACITY_THRESHOLD * 100);
    }

    /**
     * Calculate the occupancy percentage of a station.
     */
    private double calculateOccupancyPercentage(BikeStation station) {
        if (station.getCapacity() == null || station.getCapacity() == 0) {
            return 0.0;
        }

        int currentCount = station.getCurrentBikeCount() != null ? station.getCurrentBikeCount() : 0;
        return (double) currentCount / station.getCapacity() * 100.0;
    }
}

