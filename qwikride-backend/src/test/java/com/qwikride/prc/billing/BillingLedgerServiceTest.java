package com.qwikride.prc.billing;

import com.qwikride.prc.domain.PaymentStatus;
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.pricing.domain.ChargeLine;
import com.qwikride.prc.pricing.domain.FinalizedBill;
import com.qwikride.prc.pricing.domain.TripFacts;
import com.qwikride.prc.repository.LedgerEntryRepository;
import com.qwikride.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingLedgerServiceTest {
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BillingLedgerService billingLedgerService;

    @Test
    void appendTripEntryPersistsComputedLedger() {
        UUID planVersionId = UUID.randomUUID();
        BigDecimal base = new BigDecimal("1.20");
        BigDecimal perMinute = new BigDecimal("0.30");
        BigDecimal surcharge = new BigDecimal("0.60");
        BigDecimal total = base.add(perMinute).add(surcharge);

        List<ChargeLine> charges = List.of(
                ChargeLine.builder().code("BASE_FEE").amount(base).meta(Map.of()).build(),
                ChargeLine.builder().code("PER_MINUTE").amount(perMinute).meta(Map.of("minutes", "10")).build(),
                ChargeLine.builder().code("E_BIKE_SURCHARGE").amount(surcharge).meta(Map.of("ebike", "true")).build());

        FinalizedBill bill = FinalizedBill.builder()
                .planVersionId(planVersionId)
                .planName("Urban Flex")
                .charges(charges)
                .total(total)
                .build();

        UUID bikeId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDateTime end = start.plusMinutes(10);
        TripFacts tripFacts = TripFacts.builder()
                .bikeId(bikeId)
                .riderId(77L)
                .startStationId(5L)
                .endStationId(8L)
                .startTime(start)
                .endTime(end)
                .distanceKm(2.5)
                .build();

        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry persisted = invocation.getArgument(0);
            persisted.setId(99L);
            return persisted;
        });
        LedgerEntry result = billingLedgerService.appendTripEntry(bill, tripFacts);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry stored = captor.getValue();

        assertEquals(tripFacts.getRiderId(), stored.getRiderId());
        assertEquals(planVersionId, stored.getPlanVersionId());
        assertEquals("Urban Flex", stored.getPlanName());
        assertEquals(bikeId, stored.getBikeId());
        assertEquals(3, stored.getCharges().size());
        assertEquals(PaymentStatus.PENDING, stored.getPaymentStatus());
        assertTrue(stored.getSummary().contains("Urban Flex"));
        assertEquals(99L, result.getId());
    }
}
