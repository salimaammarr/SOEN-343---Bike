package com.qwikride.prc.pricing;

import com.qwikride.model.Bike;
import com.qwikride.model.BikeType;
import com.qwikride.model.RideHistory;
import com.qwikride.model.User;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import com.qwikride.prc.pricing.domain.TripFacts;
import com.qwikride.prc.pricing.selector.SelectionInput;
import com.qwikride.prc.service.MembershipService;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import com.qwikride.repository.UserRepository;
import com.qwikride.service.RideHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TripFactsFactory {
        private final RideHistoryService rideHistoryService;
        private final BikeRepository bikeRepository;
        private final BikeStationRepository bikeStationRepository;
        private final MembershipService membershipService;
        private final UserRepository userRepository;

        public TripFacts buildTripFacts(UUID bikeId,
                        Long riderId,
                        Long endStationId,
                        double durationMinutes,
                        double distanceKm) {
                Optional<RideHistory> inProgress = rideHistoryService.findInProgressRide(riderId);

                LocalDateTime startTime = inProgress.map(RideHistory::getStartTime)
                                .orElse(LocalDateTime.now().minusMinutes((long) Math.ceil(durationMinutes)));
                Long startStationId = inProgress.map(RideHistory::getStartStationId).orElse(null);
                long roundedMinutes = Math.max(1L, (long) Math.ceil(durationMinutes));
                LocalDateTime endTime = startTime.plusMinutes(roundedMinutes);

                boolean ebike = bikeRepository.findById(bikeId)
                                .map(Bike::getType)
                                .map(type -> type == BikeType.E_BIKE)
                                .orElse(false);

                MembershipStatus membershipStatus = membershipService.resolveMembership(riderId);

                // Resolve pricing plan type
                PricingPlanType pricingPlanType = userRepository.findById(riderId)
                                .map(User::getPricingPlan)
                                .orElse(PricingPlanType.FREE);

                // Check if this is an operator acting as a rider
                boolean isOperatorActingAsRider = userRepository.findById(riderId)
                                .map(user -> user.getRole() == User.UserRole.OPERATOR &&
                                                user.getActiveRole() == User.UserRole.RIDER)
                                .orElse(false);

                return TripFacts.builder()
                                .bikeId(bikeId)
                                .riderId(riderId)
                                .startStationId(startStationId)
                                .endStationId(endStationId)
                                .startTime(startTime)
                                .endTime(endTime)
                                .ebike(ebike)
                                .distanceKm(distanceKm)
                                .membershipStatus(membershipStatus)
                                .pricingPlanType(pricingPlanType)
                                .cityId(resolveCityId(startStationId))
                                .isOperatorActingAsRider(isOperatorActingAsRider)
                                .build();
        }

        public SelectionInput buildSelectionInput(LocalDateTime tripEndTime, MembershipStatus membershipStatus,
                        PricingPlanType pricingPlanType, String cityId) {
                return SelectionInput.builder()
                                .tripEndTime(tripEndTime)
                                .membershipStatus(membershipStatus)
                                .pricingPlanType(pricingPlanType)
                                .cityId(cityId)
                                .build();
        }

        private String resolveCityId(Long stationId) {
                if (stationId == null) {
                        return null;
                }
                return bikeStationRepository.findById(stationId)
                                .map(station -> {
                                        String address = station.getAddress();
                                        if (address != null && address.toLowerCase().contains("montreal")) {
                                                return "MTL";
                                        }
                                        return "DEFAULT";
                                })
                                .orElse(null);
        }
}
