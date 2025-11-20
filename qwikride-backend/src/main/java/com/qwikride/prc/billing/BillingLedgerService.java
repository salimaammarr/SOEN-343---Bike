package com.qwikride.prc.billing;

import com.qwikride.prc.domain.PaymentStatus;
import com.qwikride.prc.dto.BillingEntryResponse;
import com.qwikride.prc.dto.ChargeLineResponse;
import com.qwikride.prc.dto.TripSummaryResponse;
import com.qwikride.prc.model.LedgerCharge;
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.pricing.domain.FinalizedBill;
import com.qwikride.prc.pricing.domain.TripFacts;
import com.qwikride.prc.repository.LedgerEntryRepository;
import com.qwikride.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingLedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final com.qwikride.service.FlexDollarsService flexDollarsService;

    @Transactional
    public LedgerEntry appendTripEntry(FinalizedBill bill, TripFacts tripFacts) {
        // Apply flex dollars automatically to reduce the bill total
        java.math.BigDecimal originalTotal = bill.getTotal();
        java.math.BigDecimal flexDollarsUsed = flexDollarsService.useFlexDollars(tripFacts.getRiderId(), originalTotal);
        java.math.BigDecimal finalTotal = originalTotal.subtract(flexDollarsUsed);

        LedgerEntry entry = new LedgerEntry();
        entry.setRiderId(tripFacts.getRiderId());
        entry.setPlanVersionId(bill.getPlanVersionId());
        entry.setPlanName(bill.getPlanName());
        entry.setBikeId(tripFacts.getBikeId());
        entry.setStartStationId(tripFacts.getStartStationId());
        entry.setEndStationId(tripFacts.getEndStationId());
        entry.setStartTime(tripFacts.getStartTime());
        entry.setEndTime(tripFacts.getEndTime());
        entry.setDurationMinutes(tripFacts.durationMinutes());
        entry.setDistanceKm(tripFacts.getDistanceKm());
        entry.setTotal(finalTotal);
        entry.setPaymentStatus(PaymentStatus.PENDING);

        // Build charges list, adding flex dollars credit if used
        List<LedgerCharge> charges = bill.getCharges().stream()
                .map(line -> LedgerCharge.from(line.getCode(), line.getAmount(), line.safeMeta()))
                .collect(Collectors.toList());

        // Add flex dollars credit line if flex dollars were used
        if (flexDollarsUsed.compareTo(java.math.BigDecimal.ZERO) > 0) {
            charges.add(LedgerCharge.from("FLEX_DOLLARS_CREDIT", 
                    flexDollarsUsed.negate(), 
                    java.util.Map.of("description", "Flex dollars applied")));
        }

        entry.setCharges(charges);
        entry.setSummary(
                String.format("%s • %d min • $%s", bill.getPlanName(), tripFacts.durationMinutes(), finalTotal));

        LedgerEntry saved = ledgerEntryRepository.save(entry);
        adjustRiderBalance(tripFacts.getRiderId(), finalTotal);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BillingEntryResponse> getHistory(Long riderId, LocalDateTime start, LocalDateTime end) {
        List<LedgerEntry> entries;
        if (start != null && end != null) {
            entries = ledgerEntryRepository.findByRiderAndDateRange(riderId, start, end);
        } else {
            entries = ledgerEntryRepository.findByRiderIdOrderByStartTimeDesc(riderId);
        }
        return entries.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse buildSummary(LedgerEntry entry) {
        List<ChargeLineResponse> charges = entry.getCharges().stream()
                .map(charge -> new ChargeLineResponse(charge.getCode(), charge.getAmount(), charge.metaAsMap()))
                .collect(Collectors.toList());
        return new TripSummaryResponse(
                entry.getBikeId(),
                entry.getRiderId(),
                entry.getStartStationId(),
                entry.getEndStationId(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getDurationMinutes(),
                entry.getDistanceKm(),
                entry.getTotal(),
                charges,
                entry.getPlanName());
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse getSummary(Long ledgerEntryId) {
        LedgerEntry entry = ledgerEntryRepository.findById(ledgerEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found"));
        return buildSummary(entry);
    }

    private BillingEntryResponse toResponse(LedgerEntry entry) {
        List<ChargeLineResponse> charges = entry.getCharges().stream()
                .map(charge -> new ChargeLineResponse(charge.getCode(), charge.getAmount(), charge.metaAsMap()))
                .collect(Collectors.toList());
        return new BillingEntryResponse(
                entry.getId(),
                entry.getPlanVersionId(),
                entry.getPlanName(),
                entry.getBikeId(),
                entry.getStartStationId(),
                entry.getEndStationId(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getDurationMinutes(),
                entry.getDistanceKm(),
                entry.getTotal(),
                entry.getPaymentStatus(),
                charges,
                entry.getSummary(),
                entry.getPaymentReference(),
                entry.getPaymentProcessedAt(),
                entry.getAdjustmentOfEntryId());
    }

    @Transactional
    public LedgerEntry appendAdjustment(Long riderId,
            LedgerEntry original,
            FinalizedBill adjustmentBill,
            String summaryNote) {
        LedgerEntry adjustment = new LedgerEntry();
        adjustment.setRiderId(riderId);
        adjustment.setPlanVersionId(adjustmentBill.getPlanVersionId());
        adjustment.setPlanName(adjustmentBill.getPlanName());
        adjustment.setBikeId(original.getBikeId());
        adjustment.setStartStationId(original.getStartStationId());
        adjustment.setEndStationId(original.getEndStationId());
        adjustment.setStartTime(original.getStartTime());
        adjustment.setEndTime(original.getEndTime());
        adjustment.setDurationMinutes(original.getDurationMinutes());
        adjustment.setDistanceKm(original.getDistanceKm());
        adjustment.setTotal(adjustmentBill.getTotal());
        adjustment.setPaymentStatus(PaymentStatus.ADJUSTED);
        adjustment.setPaymentProcessedAt(java.time.LocalDateTime.now());
        adjustment.setCharges(adjustmentBill.getCharges().stream()
                .map(line -> LedgerCharge.from(line.getCode(), line.getAmount(), line.safeMeta()))
                .collect(Collectors.toList()));
        adjustment.setSummary(summaryNote);
        adjustment.setAdjustmentOfEntryId(original.getId());

        LedgerEntry saved = ledgerEntryRepository.save(adjustment);
        adjustRiderBalance(riderId, adjustmentBill.getTotal());
        return saved;
    }

    private void adjustRiderBalance(Long riderId, java.math.BigDecimal delta) {
        userRepository.findById(riderId).ifPresent(user -> {
            java.math.BigDecimal current = user.getPendingBalance() == null
                    ? java.math.BigDecimal.ZERO
                    : user.getPendingBalance();
            user.setPendingBalance(current.add(delta));
            userRepository.save(user);
        });
    }
}
