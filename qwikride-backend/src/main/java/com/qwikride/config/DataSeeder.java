package com.qwikride.config;

import com.qwikride.model.*;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import com.qwikride.repository.*;
import com.qwikride.factory.BikeFactory;
import com.qwikride.factory.BikeFactoryRegistry;
import com.qwikride.service.PricingService;
import com.qwikride.service.RideHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final BikeStationRepository bikeStationRepository;
    private final BikeRepository bikeRepository;
    private final RideHistoryRepository rideHistoryRepository;
    private final PricingPlanVersionRepository pricingPlanVersionRepository;
    private final PasswordEncoder passwordEncoder;
    private final BikeFactoryRegistry bikeFactoryRegistry;
    private final RideHistoryService rideHistoryService;
    private final PricingService pricingService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🌱 Starting data seeding...");

        // Create operator
        User operator = createOperator();

        // Create test riders
        List<User> riders = createTestRiders();

        // Create bike stations
        List<BikeStation> stations = createBikeStations();

        // Create bikes
        List<Bike> bikes = createBikes(stations);

        // Create sample ride history
        createSampleRideHistory(riders, bikes, stations);

        // Create sample pricing plans
        createPricingPlans();

        log.info("✅ Data seeding completed successfully!");
        log.info("📊 Summary:");
        log.info("   - {} users created (1 operator, {} riders)", riders.size() + 1, riders.size());
        log.info("   - {} bike stations created", stations.size());
        log.info("   - {} bikes created", bikes.size());
    }

    private User createOperator() {
        if (!userRepository.existsByUsername("operator")) {
            User operator = new User();
            operator.setFullName("System Operator");
            operator.setAddress("QwikRide HQ");
            operator.setEmail("operator@qwikride.com");
            operator.setUsername("operator");
            operator.setPasswordHash(passwordEncoder.encode("operator123"));
            operator.setPaymentInfo("N/A");
            operator.setRole(User.UserRole.OPERATOR);
            operator.setMembershipStatus(MembershipStatus.ENTRY);

            userRepository.save(operator);
            log.info("✅ Operator account created (username: operator, password: operator123)");
            return operator;
        }
        return userRepository.findByUsername("operator").orElse(null);
    }

    private List<User> createTestRiders() {
        List<User> riders = new ArrayList<>();
        String[][] riderData = {
                { "John Doe", "john@example.com", "johndoe", "password123", "123 Main St, Montreal" },
                { "Jane Smith", "jane@example.com", "janesmith", "password123", "456 Oak Ave, Montreal" },
                { "Mike Johnson", "mike@example.com", "mikejohnson", "password123", "789 Pine Rd, Montreal" },
                { "Sarah Williams", "sarah@example.com", "sarahw", "password123", "321 Elm St, Montreal" },
                { "David Brown", "david@example.com", "davidbrown", "password123", "654 Maple Dr, Montreal" }
        };

        for (String[] data : riderData) {
            if (!userRepository.existsByUsername(data[2])) {
                User rider = new User();
                rider.setFullName(data[0]);
                rider.setEmail(data[1]);
                rider.setUsername(data[2]);
                rider.setPasswordHash(passwordEncoder.encode(data[3]));
                rider.setAddress(data[4]);
                rider.setPaymentInfo("Credit Card ending in 1234");
                rider.setRole(User.UserRole.RIDER);
                rider.setMembershipStatus(randomMembership());

                riders.add(userRepository.save(rider));
                log.info("✅ Created rider: {} (username: {}, password: {})", data[0], data[2], data[3]);
            } else {
                userRepository.findByUsername(data[2]).ifPresent(riders::add);
            }
        }
        return riders;
    }

    private MembershipStatus randomMembership() {
        MembershipStatus[] tiers = { MembershipStatus.ENTRY, MembershipStatus.BRONZE, MembershipStatus.SILVER, MembershipStatus.GOLD };
        return tiers[new Random().nextInt(tiers.length)];
    }

    private List<BikeStation> createBikeStations() {
        List<BikeStation> stations = new ArrayList<>();
        Object[][] stationData = {
                { "Downtown Central", "123 Main Street, Downtown", 25 },
                { "University Campus", "456 University Ave, Campus", 30 },
                { "Shopping Mall", "789 Commerce Blvd, Shopping District", 20 },
                { "Park & Ride", "321 Transit Way, Suburb", 15 },
                { "Waterfront", "555 Harbor Blvd, Waterfront", 18 }
        };

        for (Object[] data : stationData) {
            BikeStation station = new BikeStation();
            station.setName((String) data[0]);
            station.setAddress((String) data[1]);
            station.setCapacity((Integer) data[2]);
            station.setCurrentBikeCount(0);
            station.setStatus(BikeStation.StationStatus.ACTIVE);

            stations.add(bikeStationRepository.save(station));
            log.info("✅ Created station: {} (capacity: {})", data[0], data[2]);
        }
        return stations;
    }

    private List<Bike> createBikes(List<BikeStation> stations) {
        List<Bike> bikes = new ArrayList<>();
        Random random = new Random();

        // Create bikes for each station
        for (BikeStation station : stations) {
            int standardBikes = 5 + random.nextInt(5); // 5-9 standard bikes
            int eBikes = 3 + random.nextInt(4); // 3-6 e-bikes

            // Create Standard Bikes
            for (int i = 0; i < standardBikes; i++) {
                BikeConfig config = new BikeConfig();
                config.setType(BikeType.STANDARD);
                config.setStationId(station.getId());

                BikeFactory factory = bikeFactoryRegistry.getFactory(BikeType.STANDARD);
                Bike bike = factory.createBike(config);
                bike.setStatus(BikeStatus.AVAILABLE);
                bikes.add(bikeRepository.save(bike));
            }

            // Create E-Bikes
            for (int i = 0; i < eBikes; i++) {
                BikeConfig config = new BikeConfig();
                config.setType(BikeType.E_BIKE);
                config.setStationId(station.getId());

                BikeFactory factory = bikeFactoryRegistry.getFactory(BikeType.E_BIKE);
                Bike bike = factory.createBike(config);
                bike.setStatus(BikeStatus.AVAILABLE);
                bikes.add(bikeRepository.save(bike));
            }

            // Update station bike count
            station.setCurrentBikeCount(standardBikes + eBikes);
            bikeStationRepository.save(station);
        }

        log.info("✅ Created {} bikes across {} stations", bikes.size(), stations.size());
        return bikes;
    }

    @SuppressWarnings("null")
    private void createSampleRideHistory(List<User> riders, List<Bike> bikes, List<BikeStation> stations) {
        if (riders.isEmpty() || bikes.isEmpty() || stations.isEmpty()) {
            log.warn("⚠️  Cannot create ride history - missing required data");
            return;
        }

        Random random = new Random();
        int ridesToCreate = 30; // Create 30 sample rides

        for (int i = 0; i < ridesToCreate; i++) {
            User rider = riders.get(random.nextInt(riders.size()));
            Bike bike = bikes.get(random.nextInt(bikes.size()));
            BikeStation startStation = stations.get(random.nextInt(stations.size()));
            BikeStation endStation = stations.get(random.nextInt(stations.size()));

            // Ensure different stations for start and end
            while (endStation.getId().equals(startStation.getId())) {
                endStation = stations.get(random.nextInt(stations.size()));
            }

            // Create ride history entry
            LocalDateTime startTime = LocalDateTime.now().minusDays(random.nextInt(30)).minusHours(random.nextInt(24));
            double durationMinutes = 15 + random.nextDouble() * 60; // 15-75 minutes
            double distanceKm = 2 + random.nextDouble() * 8; // 2-10 km// Simple cost calculation for sample data: base fee + per-minute rate
            double baseFee = (bike.getType() == BikeType.E_BIKE) ? 3.00 : 2.00;
            double perMinuteRate = 0.25;
            double cost = baseFee + (durationMinutes * perMinuteRate);

            RideHistory rideHistory = RideHistory.builder()
                    .userId(rider.getId())
                    .bikeId(bike.getId())
                    .startStationId(startStation.getId())
                    .endStationId(endStation.getId())
                    .startTime(startTime)
                    .endTime(startTime.plusMinutes((long) durationMinutes))
                    .durationMinutes(durationMinutes)
                    .distanceKm(distanceKm)
                    .cost(cost)
                    .status(RideHistory.RideStatus.COMPLETED)
                    .bikeType(bike.getType() != null ? bike.getType().name() : "STANDARD")
                    .build();

            rideHistoryRepository.save(rideHistory);
        }

        log.info("✅ Created {} sample ride history entries", ridesToCreate);
    }

    private void createPricingPlans() {
        if (pricingPlanVersionRepository.count() > 0) {
            return;
        }

        PricingPlanVersion standard = new PricingPlanVersion();
        standard.setId(UUID.randomUUID());
        standard.setPlanName("Standard Daily");
        standard.setBaseFee(java.math.BigDecimal.valueOf(2.00));
        standard.setPerMinuteRate(java.math.BigDecimal.valueOf(0.25));
        standard.setEbikeSurcharge(java.math.BigDecimal.valueOf(1.00));
        standard.setMembershipTier(MembershipStatus.ENTRY);
        standard.setCityId("MTL");
        standard.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
        standard.setEffectiveTo(null);
        standard.setDescription("Pay-as-you-go plan with per-minute billing");
        standard.setPublished(true);

        PricingPlanVersion gold = new PricingPlanVersion();
        gold.setId(UUID.randomUUID());
        gold.setPlanName("Gold Tier Plan");
        gold.setBaseFee(java.math.BigDecimal.valueOf(0.00));
        gold.setPerMinuteRate(java.math.BigDecimal.valueOf(0.18));
        gold.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.50));
        gold.setMembershipTier(MembershipStatus.GOLD);
        gold.setCityId("MTL");
        gold.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
        gold.setEffectiveTo(null);
        gold.setDescription("Discounted rates for Gold tier members (15% discount + 5min reservation extension)");
        gold.setPublished(true);

        pricingPlanVersionRepository.save(standard);
        pricingPlanVersionRepository.save(gold);
        log.info("✅ Created sample pricing plans (Standard Daily, Gold Tier Plan)");
    }
}