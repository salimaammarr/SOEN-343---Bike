package com.qwikride.model;

import com.qwikride.prc.domain.MembershipStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    private String paymentInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.RIDER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus membershipStatus = MembershipStatus.ENTRY;

    @Column(precision = 12, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal flexDollars = BigDecimal.ZERO;

    public enum UserRole {
        RIDER, OPERATOR
    }
}