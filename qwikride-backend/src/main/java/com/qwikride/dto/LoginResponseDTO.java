package com.qwikride.dto;

import com.qwikride.prc.domain.MembershipStatus;
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
    private String role;
    private Long id;
    private MembershipStatus tier;
    private String tierChangeNotification; // null if no change, message if tier changed
}