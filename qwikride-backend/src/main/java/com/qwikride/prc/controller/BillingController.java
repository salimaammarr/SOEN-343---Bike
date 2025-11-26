package com.qwikride.prc.controller;

import com.qwikride.model.User;
import com.qwikride.prc.billing.BillingLedgerService;
import com.qwikride.prc.dto.BillingEntryResponse;
import com.qwikride.prc.dto.SettlePaymentRequest;
import com.qwikride.prc.dto.TripSummaryResponse;
import com.qwikride.prc.model.LedgerEntry;
import com.qwikride.prc.repository.LedgerEntryRepository;
import com.qwikride.prc.service.BillingDocumentService;
import com.qwikride.prc.service.PaymentSettlementService;
import com.qwikride.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@RestController
@RequestMapping("/api/prc/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingLedgerService billingLedgerService;
    private final PaymentSettlementService paymentSettlementService;
    private final BillingDocumentService billingDocumentService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    @GetMapping("/history/{riderId}")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<Page<BillingEntryResponse>> getHistory(
            @PathVariable Long riderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {

        assertRiderScope(riderId);
        LocalDateTime normalizedStart = normalizeStart(start);
        LocalDateTime normalizedEnd = normalizeEnd(end);
        return ResponseEntity.ok(billingLedgerService.getHistory(riderId, normalizedStart, normalizedEnd, pageable));
    }

    @GetMapping("/summary/{ledgerEntryId}")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<TripSummaryResponse> getSummary(@PathVariable Long ledgerEntryId) {
        requireLedgerAccess(ledgerEntryId);
        return ResponseEntity.ok(billingLedgerService.getSummary(ledgerEntryId));
    }

    @PostMapping("/settle/{ledgerEntryId}")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<?> settlePayment(@PathVariable Long ledgerEntryId,
            @RequestParam(required = false) Long riderId,
            @RequestBody(required = false) SettlePaymentRequest request) {
        LedgerEntry entry = requireLedgerAccess(ledgerEntryId);
        Long effectiveRiderId = Optional.ofNullable(riderId).orElse(entry.getRiderId());
        assertRiderScope(effectiveRiderId);
        String paymentMethod = request != null ? request.paymentMethodToken() : "saved-default";
        return ResponseEntity.ok(paymentSettlementService.settle(effectiveRiderId, ledgerEntryId, paymentMethod));
    }

    @GetMapping("/receipt/{ledgerEntryId}")
    @PreAuthorize("hasAuthority('RIDER') or hasAuthority('OPERATOR')")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long ledgerEntryId) {
        LedgerEntry entry = requireLedgerAccess(ledgerEntryId);
        byte[] pdf = billingDocumentService.buildReceipt(entry);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + ledgerEntryId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('OPERATOR')")
    public ResponseEntity<byte[]> exportLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(entry -> start == null || !entry.getStartTime().isBefore(start))
                .filter(entry -> end == null || !entry.getEndTime().isAfter(end))
                .collect(Collectors.toList());
        byte[] csv = billingDocumentService.buildCsv(entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ledger-export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private LocalDateTime normalizeStart(LocalDateTime input) {
        return input;
    }

    private LocalDateTime normalizeEnd(LocalDateTime input) {
        return input;
    }

    private void assertRiderScope(Long riderId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
        }
        boolean operator = authentication.getAuthorities().stream()
                .anyMatch(a -> "OPERATOR".equalsIgnoreCase(a.getAuthority()));
        if (operator) {
            return;
        }
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
        if (!user.getId().equals(riderId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot access billing history for another rider");
        }
    }

    private LedgerEntry requireLedgerAccess(Long ledgerEntryId) {
        LedgerEntry entry = ledgerEntryRepository.findById(ledgerEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found"));
        assertRiderScope(entry.getRiderId());
        return entry;
    }
}
