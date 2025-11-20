package com.qwikride.dto;

import com.qwikride.model.User;
import com.qwikride.prc.domain.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDTO {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private User.UserRole role;
    private User.UserRole activeRole;
    private Boolean hasDualRole;
    private MembershipStatus tier;
    private BigDecimal flexDollars;
    private BigDecimal pendingBalance;
}

