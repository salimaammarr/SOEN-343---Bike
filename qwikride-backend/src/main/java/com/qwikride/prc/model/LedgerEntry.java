package com.qwikride.prc.model;

import com.qwikride.prc.domain.PaymentStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long riderId;

    @Column(nullable = false)
    private UUID planVersionId;

    @Column(nullable = false)
    private String planName;

    @Column(nullable = true)
    private UUID bikeId;

    @Column(nullable = true)
    private Long startStationId;

    @Column(nullable = true)
    private Long endStationId;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private long durationMinutes;

    @Column
    private double distanceKm;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(length = 64)
    private String paymentReference;

    @Column
    private LocalDateTime paymentProcessedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ledger_entry_charges", joinColumns = @JoinColumn(name = "ledger_entry_id"))
    private List<LedgerCharge> charges = new ArrayList<>();

    @Column(length = 128)
    private String summary;

    @Column
    private Long adjustmentOfEntryId;

    public boolean isImmutable() {
        return true;
    }
}
