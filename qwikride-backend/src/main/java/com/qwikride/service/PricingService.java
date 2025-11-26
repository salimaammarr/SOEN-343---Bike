package com.qwikride.service;

import com.qwikride.event.BikeReservedEvent;
import com.qwikride.event.DomainEvent;
import com.qwikride.event.EventSubscriber;
import com.qwikride.event.TripEndedEvent;
import com.qwikride.prc.billing.BillingLedgerService;
import com.qwikride.prc.dto.TripSummaryResponse;
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.pricing.PricingEngine;
import com.qwikride.prc.pricing.TripFactsFactory;
import com.qwikride.prc.pricing.domain.FinalizedBill;
import com.qwikride.prc.pricing.domain.TripFacts;
import com.qwikride.prc.pricing.selector.SelectionInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService implements EventSubscriber {
    private final PricingEngine pricingEngine;
    private final TripFactsFactory tripFactsFactory;
    private final BillingLedgerService billingLedgerService;

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof TripEndedEvent tripEndedEvent) {
            handleTripEnded(tripEndedEvent);
        } else if (event instanceof BikeReservedEvent bikeReservedEvent) {
            log.debug("Pricing Service: Bike {} reserved by user {}", bikeReservedEvent.getBikeId(),
                    bikeReservedEvent.getUserId());
        }
    }

    private void handleTripEnded(TripEndedEvent event) {
        TripFacts tripFacts = tripFactsFactory.buildTripFacts(
                event.getBikeId(),
                event.getUserId(),
                event.getReturnStationId(),
                event.getDurationMinutes(),
                event.getDistanceKm());

        SelectionInput selectionInput = tripFactsFactory.buildSelectionInput(
                tripFacts.getEndTime(),
                tripFacts.getMembershipStatus(),
                tripFacts.getPricingPlanType(),
                tripFacts.getCityId());

        FinalizedBill bill = pricingEngine.price(tripFacts, selectionInput);
        LedgerEntry ledgerEntry = billingLedgerService.appendTripEntry(bill, tripFacts);

        // Ensure downstream listeners (history, UI) see deterministic cost
        event.setCost(bill.getTotal().doubleValue());

        TripSummaryResponse summary = billingLedgerService.buildSummary(ledgerEntry);
        log.info("Trip summary computed for rider {} using plan {}: total={}, charges={}",
                tripFacts.getRiderId(),
                summary.planName(),
                summary.total(),
                summary.charges().size());
    }
}
