package com.qwikride.config;

import com.qwikride.model.*;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
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
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.model.LedgerCharge;
import com.qwikride.prc.domain.PaymentStatus;
import com.qwikride.prc.repository.LedgerEntryRepository;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final BikeStationRepository bikeStationRepository;
    private final BikeRepository bikeRepository;
    private final BikeFactoryRegistry bikeFactoryRegistry;
    private final PasswordEncoder passwordEncoder;
    private final RideHistoryRepository rideHistoryRepository;
    private final ReservationHistoryRepository reservationHistoryRepository;
    private final PricingPlanVersionRepository pricingPlanVersionRepository;
    private final CleanupService cleanupService;
    private final LedgerEntryRepository ledgerEntryRepository;

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

        // Create subscription test users
        createSubscriptionTestUsers(bikes, allStations);

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
     * 2. Bronze → Silver: 11 trips in last year, 4 trips in current month (needs 1
     * more to reach 5/month)
     * 3. Silver → Gold: 11 trips in last year, 5 trips/month, 4 trips in current
     * week (needs 1 more to reach 5/week)
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
        // Create 10 trips in last year (older than 3 months to avoid affecting monthly
        // counts)
        createRidesForUser(entryUser, bikes, stations, 10, now.minusMonths(6), now.minusMonths(4), random);
        log.info("✅ Created Entry→Bronze demo user: demoentry (10 trips, needs 1 more to reach Bronze)");

        // User 2: Bronze → Silver (11 trips in last year, 4 trips in current month,
        // needs 1 more)
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

        // User 3: Silver → Gold (11 trips in last year, 5 trips/month, 4 trips in
        // current week, needs 1 more)
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
        if (bikes.isEmpty() || stations.isEmpty())
            return;

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

            // Create corresponding Ledger Entry for billing history
            createLedgerEntryForRide(user, rideHistory, bike, startStation, endStation);
        }
    }

    private void createLedgerEntryForRide(User user, RideHistory ride, Bike bike, BikeStation startStation,
            BikeStation endStation) {
        LedgerEntry entry = new LedgerEntry();
        entry.setRiderId(user.getId());
        entry.setPlanVersionId(UUID.randomUUID()); // Placeholder
        entry.setPlanName(user.getPricingPlan().name());
        entry.setBikeId(bike.getId());
        entry.setStartStationId(startStation.getId());
        entry.setEndStationId(endStation.getId());
        entry.setStartTime(ride.getStartTime());
        entry.setEndTime(ride.getEndTime());
        entry.setDurationMinutes((long) (double) ride.getDurationMinutes());
        entry.setDistanceKm(ride.getDistanceKm());
        entry.setTotal(BigDecimal.valueOf(ride.getCost()));
        entry.setPaymentStatus(PaymentStatus.PAID);
        entry.setPaymentReference("SEED-" + UUID.randomUUID().toString().substring(0, 8));
        entry.setPaymentProcessedAt(ride.getEndTime());
        entry.setSummary("Ride from " + startStation.getName() + " to " + endStation.getName());

        // Add charges
        List<LedgerCharge> charges = new ArrayList<>();
        charges.add(LedgerCharge.from("UNLOCK_FEE", BigDecimal.valueOf(bike.getType() == BikeType.E_BIKE ? 3.00 : 2.00),
                null));
        charges.add(LedgerCharge.from("RIDE_DURATION",
                BigDecimal.valueOf(ride.getCost() - (bike.getType() == BikeType.E_BIKE ? 3.00 : 2.00)), null));
        entry.setCharges(charges);

        ledgerEntryRepository.save(entry);
    }

    private void createReservationsForUser(User user, List<Bike> bikes, List<BikeStation> stations,
            int count, LocalDateTime startTime, LocalDateTime endTime, Random random) {
        if (bikes.isEmpty() || stations.isEmpty())
            return;

        for (int i = 0; i < count; i++) {
            Bike bike = bikes.get(random.nextInt(bikes.size()));
            BikeStation station = stations.get(random.nextInt(stations.size()));

            // Random time within the range
            long secondsBetween = java.time.Duration.between(startTime, endTime).getSeconds();
            LocalDateTime reservationTime = startTime.plusSeconds(random.nextInt((int) Math.max(1, secondsBetween)));
            LocalDateTime completionTime = reservationTime.plusMinutes(10 + random.nextInt(20));

            ReservationHistory reservation = ReservationHistory.builder()
                    .userId(user.getId())
                    .bikeId(bike.getId())
                    .stationId(station.getId())
                    .reservationTime(reservationTime)
                    .completionTime(completionTime)
                    .status(ReservationHistory.ReservationStatus.CLAIMED)
                    .build();

            reservationHistoryRepository.save(reservation);
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
            // Check if station already has bikes
            List<Bike> existingBikes = bikeRepository.findByStationId(station.getId());
            if (!existingBikes.isEmpty()) {
                log.info("⏭️  Station {} already has {} bikes. Skipping creation.", station.getName(),
                        existingBikes.size());
                bikes.addAll(existingBikes);
                continue;
            }

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

    private void createSubscriptionTestUsers(List<Bike> bikes, List<BikeStation> stations) {
        LocalDateTime now = LocalDateTime.now();
        Random random = new Random();

        // 1. Pay As You Go User
        User payg = createOrResetDemoUser("payg_user", "Pay As You Go User", "payg@test.com", "password123",
                "100 Payg St", MembershipStatus.ENTRY);
        payg.setPricingPlan(com.qwikride.prc.domain.PricingPlanType.FREE);
        userRepository.save(payg);
        log.info("✅ Created Pay As You Go user: payg_user");

        // 2. Monthly Subscriber (Silver Tier)
        User monthly = createOrResetDemoUser("monthly_user", "Monthly Subscriber", "monthly@test.com", "password123",
                "200 Monthly Ave", MembershipStatus.SILVER);
        monthly.setPricingPlan(com.qwikride.prc.domain.PricingPlanType.REGULAR);
        userRepository.save(monthly);

        // Generate rides to maintain Silver status
        rideHistoryRepository.deleteAll(rideHistoryRepository.findByUserIdOrderByStartTimeDesc(monthly.getId()));
        reservationHistoryRepository.deleteAll(reservationHistoryRepository.findByUserId(monthly.getId()));

        // > 10 trips in last year
        createRidesForUser(monthly, bikes, stations, 15, now.minusMonths(6), now.minusMonths(4), random);
        // > 5 trips/month for last 3 months
        createRidesForUser(monthly, bikes, stations, 6, now.minusMonths(3), now.minusMonths(2), random);
        createRidesForUser(monthly, bikes, stations, 6, now.minusMonths(2), now.minusMonths(1), random);
        createRidesForUser(monthly, bikes, stations, 6, now.minusMonths(1), now, random);

        // > 5 claimed reservations (Required for Silver)
        createReservationsForUser(monthly, bikes, stations, 10, now.minusMonths(6), now.minusMonths(1), random);

        log.info("✅ Created Monthly user: monthly_user (Silver Status Secured)");

        // 3. Yearly Subscriber (Gold Tier)
        User yearly = createOrResetDemoUser("yearly_user", "Yearly Subscriber", "yearly@test.com", "password123",
                "300 Yearly Rd", MembershipStatus.GOLD);
        yearly.setPricingPlan(com.qwikride.prc.domain.PricingPlanType.PRO);
        userRepository.save(yearly);

        // Generate rides to maintain Gold status
        rideHistoryRepository.deleteAll(rideHistoryRepository.findByUserIdOrderByStartTimeDesc(yearly.getId()));
        reservationHistoryRepository.deleteAll(reservationHistoryRepository.findByUserId(yearly.getId()));

        // > 10 trips in last year
        createRidesForUser(yearly, bikes, stations, 15, now.minusMonths(6), now.minusMonths(4), random);
        // > 5 trips/month for last 3 months
        createRidesForUser(yearly, bikes, stations, 6, now.minusMonths(3), now.minusMonths(2), random);
        createRidesForUser(yearly, bikes, stations, 6, now.minusMonths(2), now.minusMonths(1), random);
        createRidesForUser(yearly, bikes, stations, 6, now.minusMonths(1), now, random);
        // > 5 trips/week for last 12 weeks
        LocalDateTime weekStart = now.minusWeeks(12);
        for (int week = 0; week < 12; week++) {
            LocalDateTime weekEnd = weekStart.plusWeeks(1);
            createRidesForUser(yearly, bikes, stations, 6, weekStart, weekEnd, random);
            weekStart = weekEnd;
        }

        // > 5 claimed reservations (Required for Silver -> Gold)
        createReservationsForUser(yearly, bikes, stations, 10, now.minusMonths(6), now.minusMonths(1), random);

        log.info("✅ Created Yearly user: yearly_user (Gold Status Secured)"); // 4. User with Debt
        User debt = createOrResetDemoUser("debt_user", "Debt User", "debt@test.com", "password123", "400 Debt Ln",
                MembershipStatus.ENTRY);
        debt.setPendingBalance(new java.math.BigDecimal("15.50"));
        debt.setPricingPlan(com.qwikride.prc.domain.PricingPlanType.FREE);
        userRepository.save(debt);
        log.info("✅ Created Debt user: debt_user (Balance: $15.50)");

        // 5. User with Flex Dollars
        User flex = createOrResetDemoUser("flex_user", "Flex User", "flex@test.com", "password123", "500 Flex Blvd",
                MembershipStatus.ENTRY);
        flex.setFlexDollars(new java.math.BigDecimal("25.00"));
        flex.setPricingPlan(com.qwikride.prc.domain.PricingPlanType.FREE);
        userRepository.save(flex);
        log.info("✅ Created Flex user: flex_user (Flex: $25.00)");
    }

    private void createPricingPlans() {
        // Delete all plans that don't match the new naming scheme
        List<PricingPlanVersion> existingPlans = pricingPlanVersionRepository.findAll();
        for (PricingPlanVersion plan : existingPlans) {
            String planName = plan.getPlanName();
            if (planName != null &&
                    !planName.equals("Pay As You Go") &&
                    !planName.equals("Monthly Plan") &&
                    !planName.equals("Yearly Plan")) {
                pricingPlanVersionRepository.delete(plan);
                log.info("🗑️  Deleted old plan: {}", planName);
            }
        }

        // Check which plans exist
        List<PricingPlanVersion> currentPlans = pricingPlanVersionRepository.findAll();
        boolean hasFree = currentPlans.stream().anyMatch(p -> "Pay As You Go".equals(p.getPlanName()));
        boolean hasRegular = currentPlans.stream().anyMatch(p -> "Monthly Plan".equals(p.getPlanName()));
        boolean hasPro = currentPlans.stream().anyMatch(p -> "Yearly Plan".equals(p.getPlanName()));

        // Create Free Plan (Pay As You Go)
        if (!hasFree) {
            PricingPlanVersion free = new PricingPlanVersion();
            free.setId(UUID.randomUUID());
            free.setPlanName("Pay As You Go");
            free.setBaseFee(java.math.BigDecimal.valueOf(1.00)); // Unlock fee
            free.setSubscriptionPrice(java.math.BigDecimal.ZERO); // No monthly fee
            free.setPerMinuteRate(java.math.BigDecimal.valueOf(0.30));
            free.setEbikeSurcharge(java.math.BigDecimal.valueOf(1.00));
            free.setMembershipTier(MembershipStatus.ENTRY);
            free.setPlanType(PricingPlanType.FREE);
            free.setCityId("MTL");
            free.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            free.setEffectiveTo(null);
            free.setDescription("No monthly fee. Pay per ride.");
            free.setPublished(true);
            pricingPlanVersionRepository.save(free);
            log.info("✅ Created Pay As You Go Plan");
        }

        // Create Regular Plan (Monthly)
        if (!hasRegular) {
            PricingPlanVersion regular = new PricingPlanVersion();
            regular.setId(UUID.randomUUID());
            regular.setPlanName("Monthly Plan");
            regular.setBaseFee(java.math.BigDecimal.ZERO); // No unlock fee
            regular.setSubscriptionPrice(java.math.BigDecimal.valueOf(15.00)); // Monthly fee
            regular.setPerMinuteRate(java.math.BigDecimal.valueOf(0.20));
            regular.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.50));
            regular.setMembershipTier(MembershipStatus.SILVER);
            regular.setPlanType(PricingPlanType.REGULAR);
            regular.setCityId("MTL");
            regular.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            regular.setEffectiveTo(null);
            regular.setDescription("Monthly subscription. No unlock fees.");
            regular.setPublished(true);
            pricingPlanVersionRepository.save(regular);
            log.info("✅ Created Monthly Plan");
        }

        // Create Pro Plan (Yearly)
        if (!hasPro) {
            PricingPlanVersion pro = new PricingPlanVersion();
            pro.setId(UUID.randomUUID());
            pro.setPlanName("Yearly Plan");
            pro.setBaseFee(java.math.BigDecimal.ZERO); // No unlock fee
            pro.setSubscriptionPrice(java.math.BigDecimal.valueOf(100.00)); // Yearly fee
            pro.setPerMinuteRate(java.math.BigDecimal.valueOf(0.15));
            pro.setEbikeSurcharge(java.math.BigDecimal.valueOf(0.25));
            pro.setMembershipTier(MembershipStatus.GOLD);
            pro.setPlanType(PricingPlanType.PRO);
            pro.setCityId("MTL");
            pro.setEffectiveFrom(LocalDateTime.now().minusMonths(1));
            pro.setEffectiveTo(null);
            pro.setDescription("Best value! Yearly subscription with lowest rates.");
            pro.setPublished(true);
            pricingPlanVersionRepository.save(pro);
            log.info("✅ Created Yearly Plan");
        }

        log.info("✅ Pricing plans initialized (Free, Regular, Pro)");
    }
}