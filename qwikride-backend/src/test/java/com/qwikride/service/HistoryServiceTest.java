package com.qwikride.service;

import com.qwikride.event.BikeMovedEvent;
import com.qwikride.event.TripEndedEvent;
import com.qwikride.event.TripStartedEvent;
import com.qwikride.model.Bike;
import com.qwikride.model.BikeType;
import com.qwikride.model.RideHistory;
import com.qwikride.repository.BikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock private RideHistoryService rideHistoryService;
    @Mock private BikeRepository bikeRepository;

    @InjectMocks
    private HistoryService historyService;

    @Test
    void onEvent_TripStarted_CreatesRideHistory() {
        UUID bikeId = UUID.randomUUID();
        Long userId = 100L;
        Long stationId = 1L;
        TripStartedEvent event = new TripStartedEvent(bikeId, userId, stationId);

        Bike bike = new com.qwikride.model.StandardBike();
        bike.setType(BikeType.STANDARD);
        when(bikeRepository.findById(bikeId)).thenReturn(Optional.of(bike));

        historyService.onEvent(event);

        verify(rideHistoryService).createRideHistory(eq(bikeId), eq(userId), eq(stationId), eq("STANDARD"));
    }

    @Test
    void onEvent_TripEnded_CompletesRideHistory() {
        UUID bikeId = UUID.randomUUID();
        Long userId = 100L;
        Long returnStationId = 2L;
        Long historyId = 555L;
        
        TripEndedEvent event = new TripEndedEvent(bikeId, userId, returnStationId, 30.0, 5.0, 10.0);

        RideHistory inProgressRide = new RideHistory();
        inProgressRide.setId(historyId);
        
        when(rideHistoryService.findInProgressRide(userId)).thenReturn(Optional.of(inProgressRide));

        historyService.onEvent(event);

        verify(rideHistoryService).findInProgressRide(userId);
        verify(rideHistoryService).updateRideHistory(
                eq(historyId),
                eq(returnStationId),
                eq(30.0),
                eq(5.0), 
                eq(10.0)
        );
    }
}
