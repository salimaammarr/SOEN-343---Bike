package com.qwikride.service;

import com.qwikride.event.TripEndedEvent;
import com.qwikride.prc.billing.BillingLedgerService;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.dto.TripSummaryResponse;
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.pricing.PricingEngine;
import com.qwikride.prc.pricing.TripFactsFactory;
import com.qwikride.prc.pricing.domain.FinalizedBill;
import com.qwikride.prc.pricing.domain.TripFacts;
import com.qwikride.prc.pricing.selector.SelectionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

        @Mock
        private PricingEngine pricingEngine;
        @Mock
        private TripFactsFactory tripFactsFactory;
        @Mock
        private BillingLedgerService billingLedgerService;

        @InjectMocks
        private PricingService pricingService;

        @Test
        void onEvent_TripEnded_CalculatesPriceAndSavesLedger() {
                // Setup
                TripEndedEvent event = new TripEndedEvent(UUID.randomUUID(), 100L, 2L, 30.0, 5.0, 0.0);

                TripFacts facts = TripFacts.builder()
                                .riderId(100L)
                                .bikeId(UUID.randomUUID())
                                .startStationId(1L)
                                .endStationId(2L)
                                .startTime(LocalDateTime.now().minusMinutes(30))
                                .endTime(LocalDateTime.now())
                                .distanceKm(5.0)
                                .membershipStatus(MembershipStatus.BRONZE)
                                .cityId("1")
                                .build();

                SelectionInput input = SelectionInput.builder()
                                .tripEndTime(LocalDateTime.now())
                                .membershipStatus(MembershipStatus.BRONZE)
                                .cityId("1")
                                .build();

                FinalizedBill bill = FinalizedBill.builder()
                                .planVersionId(UUID.randomUUID())
                                .planName("Standard")
                                .charges(Collections.emptyList())
                                .total(BigDecimal.TEN)
                                .build();
                LedgerEntry entry = new LedgerEntry();

                TripSummaryResponse summary = new TripSummaryResponse(
                                UUID.randomUUID(), 100L, 1L, 2L, LocalDateTime.now(), LocalDateTime.now(),
                                30L, 5.0, BigDecimal.TEN, Collections.emptyList(), "Standard");

                when(tripFactsFactory.buildTripFacts(any(), any(), any(), anyDouble(), anyDouble())).thenReturn(facts);
                when(tripFactsFactory.buildSelectionInput(any(), any(), any(), any())).thenReturn(input);
                when(pricingEngine.price(facts, input)).thenReturn(bill);
                when(billingLedgerService.appendTripEntry(bill, facts)).thenReturn(entry);
                when(billingLedgerService.buildSummary(entry)).thenReturn(summary);

                // Action
                pricingService.onEvent(event);

                // Verify
                verify(pricingEngine).price(facts, input);
                verify(billingLedgerService).appendTripEntry(bill, facts);
                // Verify event cost was updated
                assert (event.getCost() == 10.0);
        }
}
