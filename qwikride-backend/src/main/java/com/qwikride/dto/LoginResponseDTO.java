package com.qwikride.dto;

import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.prc.domain.PricingPlanType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String username;
    private String fullName;
    private String role; // Active role
    private Long id;
    private MembershipStatus tier;
    private PricingPlanType pricingPlan;
    private String tierChangeNotification; // null if no change, message if tier changed
    private Boolean hasDualRole; // true if user can toggle between OPERATOR and RIDER
    private String primaryRole; // Primary role (OPERATOR for dual-role users)
    private java.math.BigDecimal flexDollars;
}