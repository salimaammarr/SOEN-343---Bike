package com.qwikride.repository;

import com.qwikride.model.ReservationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReservationHistoryRepository extends JpaRepository<ReservationHistory, Long> {
    long countByUserIdAndStatusAndCompletionTimeAfter(Long userId, ReservationHistory.ReservationStatus status,
            LocalDateTime time);
}
