package com.qwikride.service;

import com.qwikride.model.Bike;
import com.qwikride.model.BikeStatus;
import com.qwikride.model.StandardBike;
import com.qwikride.repository.BikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BikeServiceTest {

    @Mock
    private BikeRepository bikeRepository;

    @InjectMocks
    private BikeService bikeService;

    @Test
    void getBike_ReturnsBike() {
        UUID bikeId = UUID.randomUUID();
        Bike bike = new StandardBike();
        bike.setId(bikeId);
        bike.setStatus(BikeStatus.AVAILABLE);

        when(bikeRepository.findById(bikeId)).thenReturn(Optional.of(bike));

        Optional<Bike> result = bikeService.getBikeById(bikeId);

        assertTrue(result.isPresent());
        assertEquals(bikeId, result.get().getId());
    }
}
