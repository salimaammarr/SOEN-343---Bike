package com.qwikride.service;

import com.qwikride.model.BikeStation;
import com.qwikride.model.User;
import com.qwikride.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlexDollarsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FlexDollarsService flexDollarsService;

    @Test
    void awardOverflowCredit_AddsBalance() {
        Long userId = 100L;
        User user = new User();
        user.setId(userId);
        user.setFlexDollars(BigDecimal.valueOf(10.00));

        BikeStation station = new BikeStation();
        station.setId(1L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal awarded = flexDollarsService.awardOverflowCredit(station, userId);

        assertEquals(BigDecimal.valueOf(1.00), awarded);
        assertEquals(BigDecimal.valueOf(11.00), user.getFlexDollars());
        verify(userRepository).save(user);
    }

    @Test
    void awardFlexDollarsIfEligible_AwardsWhenBelowThreshold() {
        Long userId = 100L;
        User user = new User();
        user.setId(userId);
        user.setFlexDollars(BigDecimal.ZERO);

        BikeStation station = new BikeStation();
        station.setId(1L);
        station.setCapacity(10);
        station.setCurrentBikeCount(1); // 10% < 25%

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal awarded = flexDollarsService.awardFlexDollarsIfEligible(station, userId);

        assertEquals(BigDecimal.valueOf(2.00), awarded);
        assertEquals(BigDecimal.valueOf(2.00), user.getFlexDollars());
        verify(userRepository).save(user);
    }

    @Test
    void awardFlexDollarsIfEligible_DoesNotAwardWhenAboveThreshold() {
        Long userId = 100L;
        BikeStation station = new BikeStation();
        station.setId(1L);
        station.setCapacity(10);
        station.setCurrentBikeCount(5); // 50% > 25%

        BigDecimal awarded = flexDollarsService.awardFlexDollarsIfEligible(station, userId);

        assertEquals(BigDecimal.ZERO, awarded);
        verify(userRepository, never()).save(any(User.class));
    }
}
