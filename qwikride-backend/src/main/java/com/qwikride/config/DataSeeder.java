package com.qwikride.config;

import com.qwikride.model.*;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.model.PricingPlanVersion;
import com.qwikride.prc.repository.PricingPlanVersionRepository;
import com.qwikride.repository.*;
import com.qwikride.factory.BikeFactory;
import com.qwikride.factory.BikeFactoryRegistry;
import com.qwikride.service.CleanupService;
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
    private final CleanupService cleanupService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🌱 Starting data seeding...");

        // Cleanup: Keep only 3 stations and delete the rest
        cleanupService.keepOnlyThreeStations();

        // Create operator
        createOperator();

        // Create bike stations (will only create if they don't exist)
        createBikeStations();

        // Ensure we have exactly 3 stations
        List<BikeStation> allStations = bikeStationRepository.findAll();
        if (allStations.size() > 3) {
            log.warn("⚠️  More than 3 stations found. Running cleanup again...");
            cleanupService.keepOnlyThreeStations();
            allStations = bikeStationRepository.findAll();
        }

        // Create bikes for the remaining stations
        List<Bike> bikes = createBikes(allStations);

        // Create test riders with specific tier progression scenarios
        List<User> testRiders = createTierTestUsers(bikes, allStations);

        // Create sample pricing plans
        createPricingPlans();

        log.info("✅ Data seeding completed successfully!");
        log.info("📊 Summary:");
        log.info("   - {} users created (1 operator, {} test riders)", testRiders.size() + 1, testRiders.size());
        log.info("   - {} bike stations (kept 3)", allStations.size());
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

    /**
     * Create 3 test users for live demo - each just 1 ride away from next tier:
     * 1. Entry → Bronze: 10 trips in last year (needs 1 more to reach 11)
     * 2. Bronze → Silver: 11 trips in last year, 4 trips in current month (needs 1 more to reach 5/month)
     * 3. Silver → Gold: 11 trips in last year, 5 trips/month, 4 trips in current week (needs 1 more to reach 5/week)
     */
    private List<User> createTierTestUsers(List<Bike> bikes, List<BikeStation> stations) {
        List<User> riders = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Random random = new Random();

        // User 1: Entry → Bronze (10 trips in last year, needs 1 more to reach 11)
        User entryUser = createOrResetDemoUser("demoentry", "Demo Entry User", "demoentry@test.com", 
                "password123", "123 Entry St", MembershipStatus.ENTRY);
        riders.add(entryUser);
        // Delete existing rides for this user
        rideHistoryRepository.deleteAll(rideHistoryRepository.findByUserIdOrderByStartTimeDesc(entryUser.getId()));
        // Create 10 trips in last year (older than 3 months to avoid affecting monthly counts)
        createRidesForUser(entryUser, bikes, stations, 10, now.minusMonths(6), now.minusMonths(4), random);
        log.info("✅ Created Entry→Bronze demo user: demoentry (10 trips, needs 1 more to reach Bronze)");

        // User 2: Bronze → Silver (11 trips in last year, 4 trips in current month, needs 1 more)
        User bronzeUser = createOrResetDemoUser("demobronze", "Demo Bronze User", "demobronze@test.com", 
                "password123", "456 Bronze Ave", MembershipStatus.BRONZE);
        riders.add(bronzeUser);
        // Delete existing rides for this user
        rideHistoryRepository.deleteAll(rideHistoryRepository.findByUserIdOrderByStartTimeDesc(bronzeUser.getId()));
        // Create 11 trips in last year (older)
        createRidesForUser(bronzeUser, bikes, stations, 7, now.minusMonths(6), now.minusMonths(4), random);
        // Create 5 trips in month 1 of last 3 months
        createRidesForUser(bronzeUser, bikes, stations, 5, now.minusMonths(3), now.minusMonths(2), random);
        // Create 5 trips in month 2
        createRidesForUser(bronzeUser, bikes, stations, 5, now.minusMonths(2), now.minusMonths(1), random);
        // Create 4 trips in current month (month 3) - needs 1 more
        createRidesForUser(bronzeUser, bikes, stations, 4, now.minusMonths(1), now, random);
        log.info("✅ Created Bronze→Silver demo user: demobronze (11 trips, 4 in current month, needs 1 more)");

        // User 3: Silver → Gold (11 trips in last year, 5 trips/month, 4 trips in current week, needs 1 more)
        User silverUser = createOrResetDemoUser("demosilver", "Demo Silver User", "demosilver@test.com", 
                "password123", "789 Silver Rd", MembershipStatus.SILVER);
        riders.add(silverUser);
        // Delete existing rides for this user
        rideHistoryRepository.deleteAll(rideHistoryRepository.findByUserIdOrderByStartTimeDesc(silverUser.getId()));
        // Create 11 trips in last year (older)
        createRidesForUser(silverUser, bikes, stations, 11, now.minusMonths(6), now.minusMonths(4), random);
        // Create 5 trips/month for last 3 months
        createRidesForUser(silverUser, bikes, stations, 5, now.minusMonths(3), now.minusMonths(2), random);
        createRidesForUser(silverUser, bikes, stations, 5, now.minusMonths(2), now.minusMonths(1), random);
        createRidesForUser(silverUser, bikes, stations, 5, now.minusMonths(1), now, random);
        // Create 5 trips in each of the last 11 weeks (meets requirement)
        LocalDateTime weekStart = now.minusWeeks(12);
        for (int week = 0; week < 11; week++) {
            LocalDateTime weekEnd = weekStart.plusWeeks(1);
            createRidesForUser(silverUser, bikes, stations, 5, weekStart, weekEnd, random);
            weekStart = weekEnd;
        }
        // Create 4 trips in current week (week 12) - needs 1 more
        createRidesForUser(silverUser, bikes, stations, 4, weekStart, now, random);
        log.info("✅ Created Silver→Gold demo user: demosilver (5 trips/month, 4 in current week, needs 1 more)");

        return riders;
    }

    private User createUserIfNotExists(String username, String fullName, String email, 
                                      String password, String address, MembershipStatus tier) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setAddress(address);
            user.setPaymentInfo("Credit Card ending in 1234");
            user.setRole(User.UserRole.RIDER);
            user.setMembershipStatus(tier);
            return userRepository.save(user);
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Create or reset a demo user - deletes existing rides to ensure clean state
     */
    private User createOrResetDemoUser(String username, String fullName, String email, 
                                      String password, String address, MembershipStatus tier) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = new User();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setAddress(address);
            user.setPaymentInfo("Credit Card ending in 1234");
            user.setRole(User.UserRole.RIDER);
            user.setMembershipStatus(tier);
            user = userRepository.save(user);
        } else {
            // Reset tier status
            user.setMembershipStatus(tier);
            userRepository.save(user);
        }
        return user;
    }

    private void createRidesForUser(User user, List<Bike> bikes, List<BikeStation> stations, 
                                    int count, LocalDateTime startTime, LocalDateTime endTime, Random random) {
        if (bikes.isEmpty() || stations.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            Bike bike = bikes.get(random.nextInt(bikes.size()));
            BikeStation startStation = stations.get(random.nextInt(stations.size()));
            BikeStation endStation = stations.get(random.nextInt(stations.size()));

            while (endStation.getId().equals(startStation.getId())) {
                endStation = stations.get(random.nextInt(stations.size()));
            }

            // Random time within the range
            long secondsBetween = java.time.Duration.between(startTime, endTime).getSeconds();
            LocalDateTime rideStart = startTime.plusSeconds(random.nextInt((int) Math.max(1, secondsBetween)));
            double durationMinutes = 15 + random.nextDouble() * 60;
            double distanceKm = 2 + random.nextDouble() * 8;
            double baseFee = (bike.getType() == BikeType.E_BIKE) ? 3.00 : 2.00;
            double cost = baseFee + (durationMinutes * 0.25);

            RideHistory rideHistory = RideHistory.builder()
                    .userId(user.getId())
                    .bikeId(bike.getId())
                    .startStationId(startStation.getId())
                    .endStationId(endStation.getId())
                    .startTime(rideStart)
                    .endTime(rideStart.plusMinutes((long) durationMinutes))
                    .durationMinutes(durationMinutes)
                    .distanceKm(distanceKm)
                    .cost(cost)
                    .status(RideHistory.RideStatus.COMPLETED)
                    .bikeType(bike.getType() != null ? bike.getType().name() : "STANDARD")
                    .build();

            rideHistoryRepository.save(rideHistory);
        }
    }

    private List<BikeStation> createBikeStations() {
        List<BikeStation> stations = new ArrayList<>();
        // Only create 3 stations
        Object[][] stationData = {
                { "Downtown Central", "123 Main Street, Downtown", 25 },
                { "University Campus", "456 University Ave, Campus", 30 },
                { "Shopping Mall", "789 Commerce Blvd, Shopping District", 20 }
        };

        for (Object[] data : stationData) {
            // Check if station with this name already exists
            boolean exists = bikeStationRepository.findAll().stream()
                    .anyMatch(s -> s.getName().equals(data[0]));
            
            if (!exists) {
                BikeStation station = new BikeStation();
                station.setName((String) data[0]);
                station.setAddress((String) data[1]);
                station.setCapacity((Integer) data[2]);
                station.setCurrentBikeCount(0);
                station.setStatus(BikeStation.StationStatus.ACTIVE);

                stations.add(bikeStationRepository.save(station));
                log.info("✅ Created station: {} (capacity: {})", data[0], data[2]);
            } else {
                log.info("⏭️  Station already exists: {}", data[0]);
            }
        }
        
        // Return all stations (existing + newly created)
        return bikeStationRepository.findAll();
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


    private void createPricingPlans() {
        // Delete all plans with old names (Standard Daily, Premium Member) or any non-tier names
        List<PricingPlanVersion> existingPlans = pricingPlanVersionRepository.findAll();
        for (PricingPlanVersion plan : existingPlans) {
            String planName = plan.getPlanName();
            // Delete plans with old names or any plan that doesn't match tier naming
            if (planName != null && 
                (planName.equals("Standard Daily") || 
                 planName.equals("Premium Member") ||
                 (!planName.contains("Entry") && 
                  !planName.contains("Bronze") && 
                  !planName.contains("Silver") && 
                  !planName.contains("Gold")))) {
                pricingPlanVersionRepository.delete(plan);
                log.info("🗑️  Deleted old plan: {}", planName);
            }
        }

        // Check if we have all tier plans
        List<PricingPlanVersion> tierPlans = pricingPlanVersionRepository.findAll().stream()
                .filter(p -> p.getPlanName() != null && 
                        (p.getPlanName().contains("Entry") || 
                         p.getPlanName().contains("Bronze") || 
                         p.getPlanName().contains("Silver") || 
                         p.getPlanName().contains("Gold")))
                .toList();
        
        // Check which tier plans exist
        boolean hasEntry = tierPlans.stream().anyMatch(p -> p.getPlanName().contains("Entry"));
        boolean hasBronze = tierPlans.stream().anyMatch(p -> p.getPlanName().contains("Bronze"));
        boolean hasSilver = tierPlans.stream().anyMatch(p -> p.getPlanName().contains("Silver"));
        boolean hasGold = tierPlans.stream().anyMatch(p -> p.getPlanName().contains("Gold"));
        
        if (hasEntry && hasBronze && hasSilver && hasGold) {
            log.info("✅ All tier plans already exist");
            return;
        }

        // Create Entry Tier Plan if it doesn't exist
        if (!hasEntry) {
            PricingPlanVersion entry = new PricingPlanVersion();
            entry.setId(UUID.randomUUID());
            entry.setPlanName("Entry Tier");
            entry.setBaseFee(java.math.BigDecimal.valueOf(2.00));
            entry.setPerMinuteRate(java.math.BigDecimal.valueOf(0.25));
            entry.setEbikeSurcharge(java.math.BigDecimal.valueOf(1.00));
            entry.setMembershipTier(MembershipStatus.ENTRY);
            entry.setCityId("MTL");
            entry.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            entry.setEffectiveTo(null);
            entry.setDescription("Pay-as-you-go plan with per-minute billing. Start riding to unlock tier benefits!");
            entry.setPublished(true);
            pricingPlanVersionRepository.save(entry);
            log.info("✅ Created Entry Tier plan");
        }

        // Create Bronze Tier Plan if it doesn't exist
        if (!hasBronze) {
            PricingPlanVersion bronze = new PricingPlanVersion();
            bronze.setId(UUID.randomUUID());
            bronze.setPlanName("Bronze Tier");
            bronze.setBaseFee(java.math.BigDecimal.valueOf(1.90));
            bronze.setPerMinuteRate(java.math.BigDecimal.valueOf(0.24));
            bronze.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.95));
            bronze.setMembershipTier(MembershipStatus.BRONZE);
            bronze.setCityId("MTL");
            bronze.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            bronze.setEffectiveTo(null);
            bronze.setDescription("5% discount on all trips. Earned after 10+ trips with perfect record.");
            bronze.setPublished(true);
            pricingPlanVersionRepository.save(bronze);
            log.info("✅ Created Bronze Tier plan");
        }

        // Create Silver Tier Plan if it doesn't exist
        if (!hasSilver) {
            PricingPlanVersion silver = new PricingPlanVersion();
            silver.setId(UUID.randomUUID());
            silver.setPlanName("Silver Tier");
            silver.setBaseFee(java.math.BigDecimal.valueOf(1.80));
            silver.setPerMinuteRate(java.math.BigDecimal.valueOf(0.23));
            silver.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.90));
            silver.setMembershipTier(MembershipStatus.SILVER);
            silver.setCityId("MTL");
            silver.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            silver.setEffectiveTo(null);
            silver.setDescription("10% discount on trips + 2-minute reservation extension. For active riders.");
            silver.setPublished(true);
            pricingPlanVersionRepository.save(silver);
            log.info("✅ Created Silver Tier plan");
        }

        // Create Gold Tier Plan if it doesn't exist
        if (!hasGold) {
            PricingPlanVersion gold = new PricingPlanVersion();
            gold.setId(UUID.randomUUID());
            gold.setPlanName("Gold Tier");
            gold.setBaseFee(java.math.BigDecimal.valueOf(1.70));
            gold.setPerMinuteRate(java.math.BigDecimal.valueOf(0.21));
            gold.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.85));
            gold.setMembershipTier(MembershipStatus.GOLD);
            gold.setCityId("MTL");
            gold.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            gold.setEffectiveTo(null);
            gold.setDescription("15% discount on trips + 5-minute reservation extension. Our most loyal riders!");
            gold.setPublished(true);
            pricingPlanVersionRepository.save(gold);
            log.info("✅ Created Gold Tier plan");
        }
        
        log.info("✅ Pricing plans initialized (Entry, Bronze, Silver, Gold tiers)");
    }
}