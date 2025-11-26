package com.qwikride.prc.repository;

import com.qwikride.prc.domain.PaymentStatus;
import com.qwikride.prc.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByRiderIdOrderByStartTimeDesc(Long riderId);

    Page<LedgerEntry> findByRiderIdOrderByStartTimeDesc(Long riderId, Pageable pageable);

    @Query("select l from LedgerEntry l where l.riderId = :riderId and l.startTime between :start and :end order by l.startTime desc")
    List<LedgerEntry> findByRiderAndDateRange(@Param("riderId") Long riderId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("select l from LedgerEntry l where l.riderId = :riderId and l.startTime between :start and :end order by l.startTime desc")
    Page<LedgerEntry> findByRiderAndDateRange(@Param("riderId") Long riderId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    List<LedgerEntry> findByPaymentStatusOrderByStartTimeDesc(PaymentStatus status);

    boolean existsByPaymentReference(String paymentReference);
}
