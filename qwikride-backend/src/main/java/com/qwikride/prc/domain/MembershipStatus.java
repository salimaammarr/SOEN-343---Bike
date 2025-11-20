package com.qwikride.prc.domain;

/**
 * Loyalty program tier status for riders.
 * Tiers are hierarchical: ENTRY < BRONZE < SILVER < GOLD
 */
public enum MembershipStatus {
    ENTRY,   // Default tier - no perks
    BRONZE,  // 5% discount on trips
    SILVER,  // 10% discount + 2-minute reservation extension
    GOLD     // 15% discount + 5-minute reservation extension
}
