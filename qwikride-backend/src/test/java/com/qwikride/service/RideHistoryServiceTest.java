package com.qwikride.service;

import com.qwikride.model.RideHistory;
import com.qwikride.repository.RideHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideHistoryServiceTest {

    @Mock
    private RideHistoryRepository rideHistoryRepository;

    @InjectMocks
    private RideHistoryService rideHistoryService;

    @Test
    void createRideHistory_SavesNewRide() {
        UUID bikeId = UUID.randomUUID();
        Long userId = 100L;
        Long stationId = 1L;
        String bikeType = "STANDARD";

        when(rideHistoryRepository.save(any(RideHistory.class))).thenAnswer(inv -> {
            RideHistory r = inv.getArgument(0);
            r.setId(123L);
            return r;
        });

        RideHistory created = rideHistoryService.createRideHistory(bikeId, userId, stationId, bikeType);

        assertNotNull(created.getId());
        assertEquals(userId, created.getUserId());
        assertEquals(bikeId, created.getBikeId());
        assertEquals(stationId, created.getStartStationId());
        assertEquals(RideHistory.RideStatus.IN_PROGRESS, created.getStatus());
        assertNotNull(created.getStartTime());
        verify(rideHistoryRepository).save(any(RideHistory.class));
    }

    @Test
    void updateRideHistory_CompletesRide() {
        Long historyId = 123L;
        Long endStationId = 2L;
        double duration = 30.0;
        double distance = 5.0;
        double cost = 10.0;

        RideHistory existing = new RideHistory();
        existing.setId(historyId);
        existing.setStatus(RideHistory.RideStatus.IN_PROGRESS);

        when(rideHistoryRepository.findById(historyId)).thenReturn(Optional.of(existing));
        when(rideHistoryRepository.save(any(RideHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        RideHistory updated = rideHistoryService.updateRideHistory(historyId, endStationId, duration, distance, cost);

        assertEquals(RideHistory.RideStatus.COMPLETED, updated.getStatus());
        assertEquals(endStationId, updated.getEndStationId());
        assertEquals(duration, updated.getDurationMinutes());
        assertEquals(distance, updated.getDistanceKm());
        assertEquals(cost, updated.getCost());
        assertNotNull(updated.getEndTime());
    }

    @Test
    void findInProgressRide_ReturnsRide() {
        Long userId = 100L;
        RideHistory ride = new RideHistory();
        ride.setUserId(userId);
        ride.setStatus(RideHistory.RideStatus.IN_PROGRESS);

        when(rideHistoryRepository.findFirstByUserIdAndStatusOrderByStartTimeDesc(userId,
                RideHistory.RideStatus.IN_PROGRESS))
                .thenReturn(Optional.of(ride));

        Optional<RideHistory> result = rideHistoryService.findInProgressRide(userId);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getUserId());
    }
}
