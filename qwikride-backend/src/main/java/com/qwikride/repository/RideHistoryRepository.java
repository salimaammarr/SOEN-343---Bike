package com.qwikride.repository;

import com.qwikride.model.RideHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RideHistoryRepository extends JpaRepository<RideHistory, Long>, JpaSpecificationExecutor<RideHistory> {
        List<RideHistory> findByUserIdOrderByStartTimeDesc(Long userId);

        List<RideHistory> findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(Long userId, LocalDateTime start,
                        LocalDateTime end);

        List<RideHistory> findByUserIdAndStartStationIdOrderByStartTimeDesc(Long userId, Long stationId);

        List<RideHistory> findByUserIdAndEndStationIdOrderByStartTimeDesc(Long userId, Long stationId);

        List<RideHistory> findByBikeIdOrderByStartTimeDesc(UUID bikeId);

        List<RideHistory> findAllByOrderByStartTimeDesc();

        List<RideHistory> findByStatus(RideHistory.RideStatus status);

        java.util.Optional<RideHistory> findFirstByUserIdAndStatusOrderByStartTimeDesc(Long userId,
                        RideHistory.RideStatus status);

        long countByUserIdAndEndTimeIsNull(Long userId);
}
