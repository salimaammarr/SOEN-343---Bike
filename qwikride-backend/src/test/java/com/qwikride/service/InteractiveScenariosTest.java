package com.qwikride.service;

import com.qwikride.adapter.BikeLocationPort;
import com.qwikride.event.*;
import com.qwikride.factory.BikeFactoryRegistry;
import com.qwikride.model.*;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import com.qwikride.repository.ReservationHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveScenariosTest {

        private StubBikeRepository bikeRepository;
        private StubBikeStationRepository bikeStationRepository;
        private StubBikeLocationPort bikeLocationPort;
        private StubBikeFactoryRegistry bikeFactoryRegistry;
        private StubFlexDollarsService flexDollarsService;
        private StubMembershipService membershipService;
        private StubReservationHistoryRepository reservationHistoryRepository;
        private StubEventBus eventBus;

        private BikeService bikeService;

        @BeforeEach
        void setUp() {
                bikeRepository = new StubBikeRepository();
                bikeStationRepository = new StubBikeStationRepository();
                bikeLocationPort = new StubBikeLocationPort();
                bikeFactoryRegistry = new StubBikeFactoryRegistry();
                flexDollarsService = new StubFlexDollarsService();
                membershipService = new StubMembershipService();
                reservationHistoryRepository = new StubReservationHistoryRepository();
                eventBus = new StubEventBus();

                bikeService = new BikeService(
                                bikeRepository,
                                bikeStationRepository,
                                eventBus,
                                bikeLocationPort,
                                bikeFactoryRegistry,
                                flexDollarsService,
                                membershipService,
                                reservationHistoryRepository);
        }

        @Test
        void testHappyPath_ReserveUnlockRideReturnBill() {
                System.out.println("=== TEST START: Happy Path (Reserve -> Unlock -> Ride -> Return) ===");
                Long userId = 100L;
                Long stationId = 1L;
                Long returnStationId = 2L;
                UUID bikeId = UUID.randomUUID();

                BikeStation stationA = new BikeStation(stationId, "Station A", "Loc A", 10, 5,
                                BikeStation.StationStatus.ACTIVE, null, null);
                BikeStation stationB = new BikeStation(returnStationId, "Station B", "Loc B", 10, 5,
                                BikeStation.StationStatus.ACTIVE, null, null);
                bikeStationRepository.save(stationA);
                bikeStationRepository.save(stationB);

                Bike bike = new StandardBike();
                bike.setId(bikeId);
                bike.setStationId(stationId);
                bike.setStatus(BikeStatus.AVAILABLE);
                bikeRepository.save(bike);

                membershipService.setMembershipStatus(userId, com.qwikride.prc.domain.MembershipStatus.BRONZE);

                // 1. Reserve
                System.out.println("Step 1: Reserving bike at Station A...");
                Bike reservedBike = bikeService.reserveBike(stationId, userId, 30);

                assertEquals(BikeStatus.RESERVED, reservedBike.getStatus());
                assertEquals(userId, reservedBike.getReservedByUserId());
                assertTrue(eventBus.hasPublished(BikeReservedEvent.class));

                // 2. Unlock
                System.out.println("Step 2: Unlocking bike (Checkout)...");
                Bike checkedOutBike = bikeService.checkoutBike(bikeId, userId);

                assertEquals(BikeStatus.IN_USE, checkedOutBike.getStatus());
                assertTrue(bikeLocationPort.isUnlocked(bikeId));
                assertTrue(eventBus.hasPublished(TripStartedEvent.class));

                // 3. Return
                System.out.println("Step 3: Returning bike at Station B...");
                flexDollarsService.setAwardAmount(BigDecimal.valueOf(2.00));

                Bike returnedBike = bikeService.returnBike(bikeId, returnStationId, userId, 15.0, 2.5);

                assertEquals(BikeStatus.AVAILABLE, returnedBike.getStatus());
                assertEquals(returnStationId, returnedBike.getStationId());
                assertTrue(bikeLocationPort.isLocked(bikeId));

                TripEndedEvent tripEvent = eventBus.getLastEvent(TripEndedEvent.class);
                assertNotNull(tripEvent);
                assertEquals(15.0, tripEvent.getDurationMinutes());
                assertEquals(2.5, tripEvent.getDistanceKm());

                assertTrue(flexDollarsService.wasAwardCalled());

                System.out.println("=== TEST END: Happy Path ===\n");
        }

        @Test
        void testStationFull_ReturnTriggersOverflow() {
                System.out.println("=== TEST START: Station Full (Overflow Credit) ===");
                Long userId = 100L;
                Long returnStationId = 2L;
                UUID bikeId = UUID.randomUUID();

                BikeStation fullStation = new BikeStation(returnStationId, "Station Full", "Loc B", 10, 10,
                                BikeStation.StationStatus.ACTIVE, null, null);
                bikeStationRepository.save(fullStation);

                Bike bike = new StandardBike();
                bike.setId(bikeId);
                bike.setStatus(BikeStatus.IN_USE);
                bike.setCurrentUserId(userId);
                bikeRepository.save(bike);

                flexDollarsService.setOverflowAmount(BigDecimal.valueOf(1.00));

                // Action
                System.out.println("Attempting to return bike to FULL station...");
                bikeService.returnBike(bikeId, returnStationId, userId, 10.0, 1.0);

                // Verify
                assertTrue(flexDollarsService.wasOverflowCalled());
                assertEquals(BikeStatus.AVAILABLE, bike.getStatus());

                System.out.println("=== TEST END: Station Full ===\n");
        }

        @Test
        void testReservationExpiry() {
                System.out.println("=== TEST START: Reservation Expiry ===");
                Bike bike = new StandardBike();
                bike.setId(UUID.randomUUID());
                bike.setStatus(BikeStatus.RESERVED);
                bike.setReservedByUserId(100L);
                bike.setReservationTime(LocalDateTime.now().minusMinutes(60));
                bike.setReservationExpiresAt(LocalDateTime.now().minusMinutes(1));
                bike.setStationId(1L);
                bikeRepository.save(bike);

                BikeStation station = new BikeStation();
                station.setId(1L);
                station.setCurrentBikeCount(5);
                bikeStationRepository.save(station);

                // Action
                System.out.println("Processing expired reservations...");
                bikeService.processExpiredReservations();

                // Verify
                assertEquals(BikeStatus.AVAILABLE, bike.getStatus());
                assertNull(bike.getReservedByUserId());
                assertTrue(eventBus.hasPublished(ReservationExpiredEvent.class));
                assertTrue(reservationHistoryRepository.wasSaveCalled());

                System.out.println("=== TEST END: Reservation Expiry ===\n");
        }

        @Test
        void testRebalancing_StationEmptiedAlert() {
                System.out.println("=== TEST START: Rebalancing Alert (Station Empty) ===");
                Long stationId = 1L;
                Long userId = 100L;

                BikeStation station = new BikeStation(stationId, "Station A", "Loc A", 10, 1,
                                BikeStation.StationStatus.ACTIVE, null, null);
                bikeStationRepository.save(station);

                Bike bike = new StandardBike();
                bike.setId(UUID.randomUUID());
                bike.setStationId(stationId);
                bike.setStatus(BikeStatus.AVAILABLE);
                bikeRepository.save(bike);

                membershipService.setMembershipStatus(userId, com.qwikride.prc.domain.MembershipStatus.BRONZE);

                // Action
                System.out.println("Reserving the LAST bike at the station...");
                bikeService.reserveBike(stationId, userId, 30);

                // Verify
                assertEquals(0, station.getCurrentBikeCount());
                assertTrue(eventBus.hasPublished(StationEmptyEvent.class));

                StationEmptyEvent emptyEvent = eventBus.getLastEvent(StationEmptyEvent.class);
                assertEquals(stationId, emptyEvent.getStationId());

                System.out.println("=== TEST END: Rebalancing Alert ===\n");
        }

        // --- STUBS ---

        static class StubEventBus extends EventBus {
                final List<DomainEvent> publishedEvents = new ArrayList<>();

                @Override
                public void publish(DomainEvent event) {
                        publishedEvents.add(event);
                }

                public boolean hasPublished(Class<? extends DomainEvent> eventClass) {
                        return publishedEvents.stream().anyMatch(eventClass::isInstance);
                }

                public <T extends DomainEvent> T getLastEvent(Class<T> eventClass) {
                        return publishedEvents.stream().filter(eventClass::isInstance).map(eventClass::cast)
                                        .reduce((first, second) -> second).orElse(null);
                }
        }

        static class StubBikeLocationPort implements BikeLocationPort {
                private final Set<UUID> unlockedBikes = new HashSet<>();

                @Override
                public boolean unlockBike(UUID bikeId) {
                        unlockedBikes.add(bikeId);
                        return true;
                }

                @Override
                public boolean lockBike(UUID bikeId) {
                        unlockedBikes.remove(bikeId);
                        return true;
                }

                @Override
                public boolean updateLocation(UUID bikeId, double latitude, double longitude) {
                        return true;
                }

                @Override
                public boolean isBikeLocked(UUID bikeId) {
                        return !unlockedBikes.contains(bikeId);
                }

                public boolean isUnlocked(UUID bikeId) {
                        return unlockedBikes.contains(bikeId);
                }

                public boolean isLocked(UUID bikeId) {
                        return !unlockedBikes.contains(bikeId);
                }
        }

        static class StubFlexDollarsService extends FlexDollarsService {
                private BigDecimal awardAmount = BigDecimal.ZERO;
                private BigDecimal overflowAmount = BigDecimal.ZERO;
                private boolean awardCalled;
                private boolean overflowCalled;

                public StubFlexDollarsService() {
                        super(null);
                }

                public void setAwardAmount(BigDecimal amount) {
                        this.awardAmount = amount;
                }

                public void setOverflowAmount(BigDecimal amount) {
                        this.overflowAmount = amount;
                }

                public boolean wasAwardCalled() {
                        return awardCalled;
                }

                public boolean wasOverflowCalled() {
                        return overflowCalled;
                }

                @Override
                public BigDecimal awardFlexDollarsIfEligible(BikeStation station, Long userId) {
                        awardCalled = true;
                        return awardAmount;
                }

                @Override
                public BigDecimal awardOverflowCredit(BikeStation station, Long userId) {
                        overflowCalled = true;
                        return overflowAmount;
                }
        }

        static class StubMembershipService extends com.qwikride.prc.service.MembershipService {
                private final Map<Long, com.qwikride.prc.domain.MembershipStatus> statusMap = new HashMap<>();

                public StubMembershipService() {
                        super(null);
                }

                public void setMembershipStatus(Long userId, com.qwikride.prc.domain.MembershipStatus status) {
                        statusMap.put(userId, status);
                }

                @Override
                public com.qwikride.prc.domain.MembershipStatus resolveMembership(Long userId) {
                        return statusMap.getOrDefault(userId, com.qwikride.prc.domain.MembershipStatus.ENTRY);
                }
        }

        abstract static class AbstractInMemoryJpaRepository<T, ID> implements JpaRepository<T, ID> {
                protected final Map<ID, T> store = new LinkedHashMap<>();
                private final Function<T, ID> idExtractor;

                protected AbstractInMemoryJpaRepository(Function<T, ID> idExtractor) {
                        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
                }

                protected ID requireId(T entity) {
                        ID id = idExtractor.apply(entity);
                        if (id == null) {
                                throw new IllegalArgumentException("Entity id cannot be null in stub repository");
                        }
                        return id;
                }

                @Override
                public void flush() {
                        // no-op
                }

                @Override
                @NonNull
                public <S extends T> S saveAndFlush(@NonNull S entity) {
                        return save(entity);
                }

                @Override
                @NonNull
                public <S extends T> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) {
                        return saveAll(entities);
                }

                @Override
                public void deleteAllInBatch(@NonNull Iterable<T> entities) {
                        deleteAll(entities);
                }

                @Override
                public void deleteAllByIdInBatch(@NonNull Iterable<ID> ids) {
                        deleteAllById(ids);
                }

                @Override
                public void deleteAllInBatch() {
                        store.clear();
                }

                @Override
                @NonNull
                public T getOne(@NonNull ID id) {
                        return getById(id);
                }

                @Override
                @NonNull
                public T getById(@NonNull ID id) {
                        return findById(id).orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
                }

                @Override
                @NonNull
                public T getReferenceById(@NonNull ID id) {
                        return getById(id);
                }

                @Override
                @NonNull
                public <S extends T> S save(@NonNull S entity) {
                        store.put(requireId(entity), entity);
                        return entity;
                }

                @Override
                @NonNull
                public <S extends T> List<S> saveAll(@NonNull Iterable<S> entities) {
                        List<S> saved = new ArrayList<>();
                        for (S entity : entities) {
                                saved.add(save(entity));
                        }
                        return saved;
                }

                @Override
                @NonNull
                public Optional<T> findById(@NonNull ID id) {
                        return Optional.ofNullable(store.get(id));
                }

                @Override
                public boolean existsById(@NonNull ID id) {
                        return store.containsKey(id);
                }

                @Override
                @NonNull
                public List<T> findAll() {
                        return new ArrayList<>(store.values());
                }

                @Override
                @NonNull
                public List<T> findAll(@NonNull Sort sort) {
                        return findAll();
                }

                @Override
                @NonNull
                public List<T> findAllById(@NonNull Iterable<ID> ids) {
                        List<T> results = new ArrayList<>();
                        for (ID id : ids) {
                                findById(id).ifPresent(results::add);
                        }
                        return results;
                }

                @Override
                public long count() {
                        return store.size();
                }

                @Override
                public void deleteById(@NonNull ID id) {
                        store.remove(id);
                }

                @Override
                public void delete(@NonNull T entity) {
                        store.remove(requireId(entity));
                }

                @Override
                public void deleteAllById(@NonNull Iterable<? extends ID> ids) {
                        for (ID id : ids) {
                                deleteById(id);
                        }
                }

                @Override
                public void deleteAll(@NonNull Iterable<? extends T> entities) {
                        for (T entity : entities) {
                                delete(entity);
                        }
                }

                @Override
                public void deleteAll() {
                        store.clear();
                }

                @Override
                @NonNull
                public Page<T> findAll(@NonNull Pageable pageable) {
                        List<T> all = findAll();
                        int start = Math.min((int) pageable.getOffset(), all.size());
                        int end = Math.min(start + pageable.getPageSize(), all.size());
                        List<T> content = all.subList(start, end);
                        return new PageImpl<>(content, pageable, all.size());
                }

                @Override
                @NonNull
                public <S extends T> Optional<S> findOne(@NonNull Example<S> example) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                @NonNull
                public <S extends T> List<S> findAll(@NonNull Example<S> example) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                @NonNull
                public <S extends T> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                @NonNull
                public <S extends T> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                public <S extends T> long count(@NonNull Example<S> example) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                public <S extends T> boolean exists(@NonNull Example<S> example) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }

                @Override
                @NonNull
                public <S extends T, R> R findBy(@NonNull Example<S> example,
                                @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
                        throw new UnsupportedOperationException("Example queries not supported in stub");
                }
        }

        static class StubReservationHistoryRepository extends AbstractInMemoryJpaRepository<ReservationHistory, Long>
                        implements ReservationHistoryRepository {
                private final AtomicLong idSequence = new AtomicLong(1);
                private boolean saveCalled;

                StubReservationHistoryRepository() {
                        super(ReservationHistory::getId);
                }

                public boolean wasSaveCalled() {
                        return saveCalled;
                }

                @Override
                @NonNull
                public <S extends ReservationHistory> S save(@NonNull S entity) {
                        if (entity.getId() == null) {
                                entity.setId(idSequence.getAndIncrement());
                        }
                        saveCalled = true;
                        return super.save(entity);
                }

                @Override
                public long countByUserIdAndStatusAndCompletionTimeAfter(Long userId,
                                ReservationHistory.ReservationStatus status, LocalDateTime time) {
                        return store.values().stream()
                                        .filter(history -> Objects.equals(history.getUserId(), userId)
                                                        && history.getStatus() == status
                                                        && history.getCompletionTime() != null
                                                        && history.getCompletionTime().isAfter(time))
                                        .count();
                }

                @Override
                public List<ReservationHistory> findByUserId(Long userId) {
                        return store.values().stream().filter(h -> Objects.equals(h.getUserId(), userId))
                                        .collect(Collectors.toList());
                }
        }

        static class StubBikeRepository extends AbstractInMemoryJpaRepository<Bike, UUID> implements BikeRepository {
                StubBikeRepository() {
                        super(Bike::getId);
                }

                @Override
                public Optional<Bike> findByReservedByUserId(Long userId) {
                        return store.values().stream().filter(b -> Objects.equals(b.getReservedByUserId(), userId))
                                        .findFirst();
                }

                @Override
                public List<Bike> findByStationIdAndStatus(Long stationId, BikeStatus status) {
                        return store.values().stream()
                                        .filter(b -> Objects.equals(b.getStationId(), stationId)
                                                        && b.getStatus() == status)
                                        .collect(Collectors.toList());
                }

                @Override
                public List<Bike> findByStatus(BikeStatus status) {
                        return store.values().stream().filter(b -> b.getStatus() == status)
                                        .collect(Collectors.toList());
                }

                @Override
                public List<Bike> findByStationId(Long stationId) {
                        return store.values().stream().filter(b -> Objects.equals(b.getStationId(), stationId))
                                        .collect(Collectors.toList());
                }

                @Override
                public List<Bike> findByCurrentUserId(Long userId) {
                        return store.values().stream().filter(b -> Objects.equals(b.getCurrentUserId(), userId))
                                        .collect(Collectors.toList());
                }
        }

        static class StubBikeStationRepository extends AbstractInMemoryJpaRepository<BikeStation, Long>
                        implements BikeStationRepository {
                StubBikeStationRepository() {
                        super(BikeStation::getId);
                }
        }

        static class StubBikeFactoryRegistry extends BikeFactoryRegistry {
                public StubBikeFactoryRegistry() {
                        super(Collections.emptyList());
                }

                @Override
                public com.qwikride.factory.BikeFactory getFactory(BikeType type) {
                        throw new UnsupportedOperationException("Factory usage not needed in these tests");
                }
        }
}