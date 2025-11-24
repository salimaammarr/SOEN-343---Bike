package com.qwikride.service;

import com.qwikride.adapter.BikeLocationPort;
import com.qwikride.event.*;
import com.qwikride.factory.BikeFactory;
import com.qwikride.factory.BikeFactoryRegistry;
import com.qwikride.model.*;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import com.qwikride.repository.ReservationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BikeService {
    private final BikeRepository bikeRepository;
    private final BikeStationRepository bikeStationRepository;
    private final EventBus eventBus;
    private final BikeLocationPort bikeLocationPort;
    private final BikeFactoryRegistry bikeFactoryRegistry;
    private final FlexDollarsService flexDollarsService;
    private final com.qwikride.prc.service.MembershipService membershipService;
    private final ReservationHistoryRepository reservationHistoryRepository;

    @Transactional
    @SuppressWarnings("null")
    public Bike createBike(BikeConfig config) {
        BikeFactory factory = bikeFactoryRegistry.getFactory(config.getType());
        Bike bike = factory.createBike(config);
        Bike savedBike = bikeRepository.save(bike);

        // Update station count
        incrementStationCount(config.getStationId());

        return savedBike;
    }

    @Transactional
    public Bike reserveBike(Long stationId, Long userId, int expiresAfterMinutes) {
        // Check if station is active and has available bikes
        BikeStation station = getStationByIdOrThrow(stationId, "Station not found");
        validateStationActive(station);

        if (station.getCurrentBikeCount() <= 0) {
            throw new IllegalStateException("No bikes available at this station");
        }

        // Check if user already has a reservation
        Optional<Bike> existingReservation = bikeRepository.findByReservedByUserId(userId);
        if (existingReservation.isPresent()) {
            throw new IllegalStateException("User already has an active reservation");
        }

        // Find an available bike at the station
        List<Bike> availableBikes = bikeRepository.findByStationIdAndStatus(stationId, BikeStatus.AVAILABLE);
        if (availableBikes.isEmpty()) {
            throw new IllegalStateException("No available bikes at this station");
        }

        // Calculate reservation time with tier-based extension
        int extendedMinutes = calculateReservationTimeWithTierExtension(userId, expiresAfterMinutes);

        Bike bike = availableBikes.get(0);
        bike.reserve(userId, extendedMinutes);
        bikeRepository.save(bike);

        // Update station count
        decrementStationCount(stationId);

        eventBus.publish(new BikeReservedEvent(bike.getId(), userId, stationId));
        return bike;
    }

    @Transactional
    public Bike checkoutBike(UUID bikeId, Long userId) {
        Bike bike = getBikeByIdOrThrow(bikeId);

        if (bike.getStatus() == BikeStatus.RESERVED && !bike.getReservedByUserId().equals(userId)) {
            throw new IllegalStateException("Bike is reserved by another user");
        }

        if (bike.isReservationExpired()) {
            recordReservationHistory(bike, ReservationHistory.ReservationStatus.EXPIRED);
            bike.cancelReservation();
            bikeRepository.save(bike);
            eventBus.publish(new ReservationExpiredEvent(bikeId, bike.getReservedByUserId()));
            throw new IllegalStateException("Reservation has expired");
        }

        if (!bike.canCheckout()) {
            throw new IllegalStateException("Bike cannot be checked out due to its status or condition");
        }

        // If bike was reserved by this user, mark reservation as claimed
        if (bike.getStatus() == BikeStatus.RESERVED && bike.getReservedByUserId().equals(userId)) {
            recordReservationHistory(bike, ReservationHistory.ReservationStatus.CLAIMED);
        }

        bike.checkout(userId);
        bikeRepository.save(bike);
        bikeLocationPort.unlockBike(bikeId);
        eventBus.publish(new TripStartedEvent(bikeId, userId, bike.getStationId()));
        return bike;
    }

    @Transactional
    public Bike returnBike(UUID bikeId, Long returnStationId, Long userId, double durationMinutes, double distanceKm) {
        Bike bike = getBikeByIdOrThrow(bikeId);

        if (bike.getStatus() != BikeStatus.IN_USE || !bike.getCurrentUserId().equals(userId)) {
            throw new IllegalStateException("Bike is not currently checked out by this user");
        }

        // Check if return station is active
        BikeStation returnStation = getStationByIdOrThrow(returnStationId, "Return station not found");
        validateStationActive(returnStation);

        // Check capacity - if full, award overflow credit
        if (returnStation.getCurrentBikeCount() >= returnStation.getCapacity()) {
            flexDollarsService.awardOverflowCredit(returnStation, userId);
        }

        bike.returnBike(returnStationId);
        bikeRepository.save(bike);
        bikeLocationPort.lockBike(bikeId);

        // Update station count
        incrementStationCount(returnStationId);

        // Refresh station to get updated count for flex dollars check
        BikeStation updatedStation = getStationByIdOrThrow(returnStationId, "Return station not found");

        // Award flex dollars if station is below 25% capacity
        flexDollarsService.awardFlexDollarsIfEligible(updatedStation, userId);

        // Publish trip completion event for pricing/billing (PricingService will
        // calculate actual cost)
        eventBus.publish(new TripEndedEvent(bikeId, userId, returnStationId, durationMinutes, distanceKm, 0.0));
        return bike;
    }

    @Transactional
    public Bike moveBike(UUID bikeId, Long newStationId, Long operatorId) {
        Bike bike = getBikeByIdOrThrow(bikeId);
        Long oldStationId = bike.getStationId();

        // Check if new station is active and has capacity
        BikeStation newStation = getStationByIdOrThrow(newStationId, "Destination station not found");
        validateStationForBikeReturn(newStation);

        if (oldStationId.equals(newStationId)) {
            throw new IllegalStateException("Cannot move bike to the same station");
        }

        bike.moveToStation(newStationId);
        bikeRepository.save(bike);

        decrementStationCount(oldStationId);
        incrementStationCount(newStationId);

        eventBus.publish(new BikeMovedEvent(bikeId, oldStationId, newStationId, operatorId));
        return bike;
    }

    @Transactional
    public void processExpiredReservations() {
        List<Bike> reservedBikes = bikeRepository.findByStatus(BikeStatus.RESERVED);
        for (Bike bike : reservedBikes) {
            if (bike.isReservationExpired()) {
                recordReservationHistory(bike, ReservationHistory.ReservationStatus.EXPIRED);
                bike.cancelReservation();
                bikeRepository.save(bike);

                // Update station count
                incrementStationCount(bike.getStationId());

                eventBus.publish(new ReservationExpiredEvent(bike.getId(), bike.getReservedByUserId()));
            }
        }
    }

    public List<Bike> getAvailableBikesAtStation(Long stationId) {
        return bikeRepository.findByStationIdAndStatus(stationId, BikeStatus.AVAILABLE);
    }

    public List<Bike> getAllBikes() {
        List<Bike> allBikes = bikeRepository.findAll();
        // Deduplicate by ID to prevent any duplicates
        Set<UUID> seenIds = new LinkedHashSet<>();
        return allBikes.stream()
                .filter(bike -> {
                    if (bike == null || bike.getId() == null)
                        return false;
                    if (seenIds.contains(bike.getId()))
                        return false;
                    seenIds.add(bike.getId());
                    return true;
                })
                .collect(Collectors.toList());
    }

    public Optional<Bike> getBikeById(UUID bikeId) {
        if (bikeId == null) {
            return Optional.empty();
        }
        return bikeRepository.findById(bikeId);
    }

    public Optional<Bike> getReservedBikeByUser(Long userId) {
        return bikeRepository.findByReservedByUserId(userId);
    }

    public List<Bike> getBikesByStation(Long stationId) {
        return bikeRepository.findByStationId(stationId);
    }

    private Bike getBikeByIdOrThrow(UUID bikeId) {
        if (bikeId == null) {
            throw new IllegalArgumentException("Bike ID cannot be null");
        }
        return bikeRepository.findById(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found"));
    }

    private BikeStation getStationByIdOrThrow(Long stationId, String errorMessage) {
        if (stationId == null) {
            throw new IllegalArgumentException("Station ID cannot be null");
        }
        return bikeStationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    private void validateStationActive(BikeStation station) {
        if (station.getStatus() != BikeStation.StationStatus.ACTIVE) {
            throw new IllegalStateException("Station is out of service");
        }
    }

    private void validateStationCapacity(BikeStation station) {
        if (station.getCurrentBikeCount() >= station.getCapacity()) {
            throw new IllegalStateException("Station is full");
        }
    }

    private void validateStationForBikeReturn(BikeStation station) {
        validateStationActive(station);
        validateStationCapacity(station);
    }

    private void incrementStationCount(Long stationId) {
        if (stationId != null) {
            bikeStationRepository.findById(stationId).ifPresent(station -> {
                station.setCurrentBikeCount(station.getCurrentBikeCount() + 1);
                bikeStationRepository.save(station);
            });
        }
    }

    private void decrementStationCount(Long stationId) {
        if (stationId != null) {
            bikeStationRepository.findById(stationId).ifPresent(station -> {
                int newCount = Math.max(0, station.getCurrentBikeCount() - 1);
                station.setCurrentBikeCount(newCount);
                bikeStationRepository.save(station);
                
                if (newCount == 0) {
                    eventBus.publish(new StationEmptyEvent(stationId));
                }
            });
        }
    }

    /**
     * Calculate reservation time with tier-based extension.
     * Bronze: +0 minutes
     * Silver: +2 minutes
     * Gold: +5 minutes
     */
    private int calculateReservationTimeWithTierExtension(Long userId, int baseMinutes) {
        com.qwikride.prc.domain.MembershipStatus tier = membershipService.resolveMembership(userId);

        int extensionMinutes = switch (tier) {
            case SILVER -> 2;
            case GOLD -> 5;
            case BRONZE, ENTRY -> 0;
        };

        return baseMinutes + extensionMinutes;
    }

    private void recordReservationHistory(Bike bike, ReservationHistory.ReservationStatus status) {
        if (bike.getReservedByUserId() != null) {
            ReservationHistory history = ReservationHistory.builder()
                    .userId(bike.getReservedByUserId())
                    .bikeId(bike.getId())
                    .stationId(bike.getStationId())
                    .reservationTime(bike.getReservationTime())
                    .completionTime(java.time.LocalDateTime.now())
                    .status(status)
                    .build();
            reservationHistoryRepository.save(history);
        }
    }
}
