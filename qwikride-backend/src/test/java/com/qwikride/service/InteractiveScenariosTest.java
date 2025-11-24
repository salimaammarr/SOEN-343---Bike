package com.qwikride.service;

import com.qwikride.adapter.BikeLocationPort;
import com.qwikride.event.*;
import com.qwikride.factory.BikeFactoryRegistry;
import com.qwikride.model.*;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import com.qwikride.repository.ReservationHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractiveScenariosTest {

    @Mock private BikeRepository bikeRepository;
    @Mock private BikeStationRepository bikeStationRepository;
    @Mock private EventBus eventBus;
    @Mock private BikeLocationPort bikeLocationPort;
    @Mock private BikeFactoryRegistry bikeFactoryRegistry;
    @Mock private FlexDollarsService flexDollarsService;
    @Mock private com.qwikride.prc.service.MembershipService membershipService;
    @Mock private ReservationHistoryRepository reservationHistoryRepository;

    @InjectMocks
    private BikeService bikeService;

    @Test
    void testHappyPath_ReserveUnlockRideReturnBill() {
        System.out.println("=== TEST START: Happy Path (Reserve -> Unlock -> Ride -> Return) ===");
        // Setup
        Long userId = 100L;
        Long stationId = 1L;
        Long returnStationId = 2L;
        UUID bikeId = UUID.randomUUID();

        BikeStation stationA = new BikeStation(stationId, "Station A", "Loc A", 10, 5, BikeStation.StationStatus.ACTIVE, null, null);
        BikeStation stationB = new BikeStation(returnStationId, "Station B", "Loc B", 10, 5, BikeStation.StationStatus.ACTIVE, null, null);
        
        Bike bike = new StandardBike();
        bike.setId(bikeId);
        bike.setStationId(stationId);
        bike.setStatus(BikeStatus.AVAILABLE);

        when(bikeStationRepository.findById(stationId)).thenReturn(Optional.of(stationA));
        when(bikeRepository.findByReservedByUserId(userId)).thenReturn(Optional.empty());
        when(bikeRepository.findByStationIdAndStatus(stationId, BikeStatus.AVAILABLE)).thenReturn(List.of(bike));
        when(membershipService.resolveMembership(userId)).thenReturn(com.qwikride.prc.domain.MembershipStatus.BRONZE);
        
        // 1. Reserve
        System.out.println("Step 1: Reserving bike at Station A...");
        Bike reservedBike = bikeService.reserveBike(stationId, userId, 30);
        System.out.println("  -> Bike reserved: " + reservedBike.getId());
        
        assertEquals(BikeStatus.RESERVED, reservedBike.getStatus());
        assertEquals(userId, reservedBike.getReservedByUserId());
        verify(eventBus).publish(any(BikeReservedEvent.class));
        verify(bikeStationRepository, atLeastOnce()).save(stationA); // Decrement count

        // 2. Unlock (Checkout)
        System.out.println("Step 2: Unlocking bike (Checkout)...");
        when(bikeRepository.findById(bikeId)).thenReturn(Optional.of(reservedBike));
        
        Bike checkedOutBike = bikeService.checkoutBike(bikeId, userId);
        System.out.println("  -> Bike unlocked and trip started.");
        
        assertEquals(BikeStatus.IN_USE, checkedOutBike.getStatus());
        verify(bikeLocationPort).unlockBike(bikeId);
        verify(eventBus).publish(any(TripStartedEvent.class));
        verify(reservationHistoryRepository).save(any(ReservationHistory.class)); // Claimed

        // 3. Return
        System.out.println("Step 3: Returning bike at Station B...");
        when(bikeStationRepository.findById(returnStationId)).thenReturn(Optional.of(stationB));
        
        Bike returnedBike = bikeService.returnBike(bikeId, returnStationId, userId, 15.0, 2.5);
        
        assertEquals(BikeStatus.AVAILABLE, returnedBike.getStatus());
        assertEquals(returnStationId, returnedBike.getStationId());
        verify(bikeLocationPort).lockBike(bikeId);
        verify(eventBus).publish(any(TripEndedEvent.class)); // Bill computed via event listener (implied)
        verify(flexDollarsService).awardFlexDollarsIfEligible(eq(stationB), eq(userId));

        System.out.println("  -> Bike returned. Trip ended.");
        System.out.println("  [Output] Trip Info: Duration=15.0min, Distance=2.5km");
        System.out.println("  [Output] Bill: (Calculated asynchronously via TripEndedEvent)");
        System.out.println("  [Output] Updated Bike State: " + returnedBike.getStatus());
        System.out.println("=== TEST END: Happy Path ===\n");
    }

    @Test
    void testStationFull_ReturnTriggersOverflow() {
        System.out.println("=== TEST START: Station Full (Overflow Credit) ===");
        // Setup
        Long userId = 100L;
        Long returnStationId = 2L;
        UUID bikeId = UUID.randomUUID();

        // Station is FULL (10/10)
        BikeStation fullStation = new BikeStation(returnStationId, "Station Full", "Loc B", 10, 10, BikeStation.StationStatus.ACTIVE, null, null);
        
        Bike bike = new StandardBike();
        bike.setId(bikeId);
        bike.setStatus(BikeStatus.IN_USE);
        bike.setCurrentUserId(userId);

        when(bikeRepository.findById(bikeId)).thenReturn(Optional.of(bike));
        when(bikeStationRepository.findById(returnStationId)).thenReturn(Optional.of(fullStation));

        // Action
        System.out.println("Attempting to return bike to FULL station...");
        bikeService.returnBike(bikeId, returnStationId, userId, 10.0, 1.0);

        // Verify
        verify(flexDollarsService).awardOverflowCredit(fullStation, userId);
        assertEquals(BikeStatus.AVAILABLE, bike.getStatus());
        // Station count should increment (overflow)
        verify(bikeStationRepository, atLeastOnce()).save(fullStation);

        System.out.println("  -> Overflow credit awarded.");
        System.out.println("  [Output] User Account Credit: Overflow Bonus Applied");
        System.out.println("  [Output] Updated Bike State: " + bike.getStatus());
        System.out.println("=== TEST END: Station Full ===\n");
    }

    @Test
    void testReservationExpiry() {
        System.out.println("=== TEST START: Reservation Expiry ===");
        // Setup
        Bike bike = new StandardBike();
        bike.setId(UUID.randomUUID());
        bike.setStatus(BikeStatus.RESERVED);
        bike.setReservedByUserId(100L);
        bike.setReservationTime(LocalDateTime.now().minusMinutes(60));
        bike.setReservationExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired
        bike.setStationId(1L);

        when(bikeRepository.findByStatus(BikeStatus.RESERVED)).thenReturn(List.of(bike));
        when(bikeStationRepository.findById(1L)).thenReturn(Optional.of(new BikeStation()));

        // Action
        System.out.println("Processing expired reservations...");
        bikeService.processExpiredReservations();

        // Verify
        assertEquals(BikeStatus.AVAILABLE, bike.getStatus());
        assertNull(bike.getReservedByUserId());
        verify(eventBus).publish(any(ReservationExpiredEvent.class));
        verify(reservationHistoryRepository).save(any(ReservationHistory.class)); // Expired history

        System.out.println("  -> Reservation expired and cancelled.");
        System.out.println("  [Output] Updated Bike State: " + bike.getStatus());
        System.out.println("=== TEST END: Reservation Expiry ===\n");
    }

    @Test
    void testRebalancing_StationEmptiedAlert() {
        System.out.println("=== TEST START: Rebalancing Alert (Station Empty) ===");
        // Setup
        Long stationId = 1L;
        Long userId = 100L;
        
        // Station has 1 bike
        BikeStation station = new BikeStation(stationId, "Station A", "Loc A", 10, 1, BikeStation.StationStatus.ACTIVE, null, null);
        
        Bike bike = new StandardBike();
        bike.setId(UUID.randomUUID());
        bike.setStationId(stationId);
        bike.setStatus(BikeStatus.AVAILABLE);

        when(bikeStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(bikeRepository.findByReservedByUserId(userId)).thenReturn(Optional.empty());
        when(bikeRepository.findByStationIdAndStatus(stationId, BikeStatus.AVAILABLE)).thenReturn(List.of(bike));
        when(membershipService.resolveMembership(userId)).thenReturn(com.qwikride.prc.domain.MembershipStatus.BRONZE);

        // Action: Reserve the last bike
        System.out.println("Reserving the LAST bike at the station...");
        bikeService.reserveBike(stationId, userId, 30);

        // Verify
        ArgumentCaptor<StationEmptyEvent> eventCaptor = ArgumentCaptor.forClass(StationEmptyEvent.class);
        verify(eventBus).publish(eventCaptor.capture());
        assertEquals(stationId, eventCaptor.getValue().getStationId());
        
        System.out.println("  -> StationEmptyEvent published for station " + stationId);
        System.out.println("  [Output] Operator Alert: Station " + stationId + " is now EMPTY!");
        System.out.println("=== TEST END: Rebalancing Alert ===\n");
    }
}
