package com.qwikride.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long amount; // Amount in cents
    private String currency;
    private String planId; // Optional: ID of the plan being purchased
    private Long ledgerEntryId; // Optional: ID of the ledger entry being paid
}
