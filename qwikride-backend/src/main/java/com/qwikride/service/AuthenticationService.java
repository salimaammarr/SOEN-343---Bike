package com.qwikride.service;

import com.qwikride.dto.LoginRequestDTO;
import com.qwikride.dto.LoginResponseDTO;
import com.qwikride.dto.RegistrationRequestDTO;
import com.qwikride.dto.UserAccountDTO;
import com.qwikride.model.User;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.repository.UserRepository;
import com.qwikride.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoyaltyTierService loyaltyTierService;

    @Transactional
    public LoginResponseDTO authenticate(LoginRequestDTO dto) {
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        // Evaluate and update tier for riders (or operators acting as riders)
        String tierChangeNotification = null;
        
        // Operators can also be riders, so evaluate tier if they have rider capabilities
        boolean isRider = user.getRole() == User.UserRole.RIDER || 
                        (user.getRole() == User.UserRole.OPERATOR && user.getActiveRole() == User.UserRole.RIDER);
        
        if (isRider || user.getRole() == User.UserRole.RIDER) {
            LoyaltyTierService.TierEvaluationResult tierResult = loyaltyTierService.evaluateAndUpdateTier(user.getId());
            
            // Refresh user to get updated tier
            user = userRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (tierResult.isChanged()) {
                MembershipStatus newTier = tierResult.getNewTier();
                MembershipStatus oldTier = tierResult.getPreviousTier();
                
                if (isTierUpgrade(oldTier, newTier)) {
                    tierChangeNotification = String.format("Congratulations! You've been upgraded to %s tier!", 
                            newTier.name());
                } else {
                    tierChangeNotification = String.format("Your tier has changed from %s to %s based on your recent activity.", 
                            oldTier.name(), newTier.name());
                }
            }
        }

        // Determine active role: use activeRole if set, otherwise use primary role
        User.UserRole activeRole = user.getActiveRole() != null ? user.getActiveRole() : user.getRole();
        
        // For operators, initialize activeRole to OPERATOR if not set
        if (user.getRole() == User.UserRole.OPERATOR && user.getActiveRole() == null) {
            user.setActiveRole(User.UserRole.OPERATOR);
            userRepository.save(user);
            activeRole = User.UserRole.OPERATOR;
        }

        String token = jwtUtil.generateToken(user.getUsername(), activeRole.name());
        
        // Determine if user has dual role capability
        boolean hasDualRole = user.getRole() == User.UserRole.OPERATOR;
        
        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getFullName(),
                activeRole.name(),
                user.getId(),
                user.getMembershipStatus(),
                tierChangeNotification,
                hasDualRole,
                user.getRole().name() // Include primary role for reference
        );
    }
    
    private boolean isTierUpgrade(MembershipStatus oldTier, MembershipStatus newTier) {
        // Compare tier levels: ENTRY < BRONZE < SILVER < GOLD
        int oldLevel = getTierLevel(oldTier);
        int newLevel = getTierLevel(newTier);
        return newLevel > oldLevel;
    }
    
    private int getTierLevel(MembershipStatus tier) {
        return switch (tier) {
            case ENTRY -> 0;
            case BRONZE -> 1;
            case SILVER -> 2;
            case GOLD -> 3;
        };
    }

    public void register(RegistrationRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = new User();
        user.setFullName(dto.getFullName());
        user.setAddress(dto.getAddress());
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setPaymentInfo(dto.getPaymentInfo());
        user.setRole(User.UserRole.RIDER); 

        userRepository.save(user);
    }

    /**
     * Toggle the active role for dual-role users (OPERATOR can switch to RIDER and back).
     */
    @Transactional
    public LoginResponseDTO toggleRole(User.UserRole newRole) {
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            throw new IllegalArgumentException("User not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Only operators can have dual roles
        if (user.getRole() != User.UserRole.OPERATOR) {
            throw new IllegalArgumentException("Only operators can toggle roles");
        }

        // Validate the new role is either OPERATOR or RIDER
        if (newRole != User.UserRole.OPERATOR && newRole != User.UserRole.RIDER) {
            throw new IllegalArgumentException("Invalid role for toggle");
        }

        user.setActiveRole(newRole);
        userRepository.save(user);

        // Generate new token with updated role
        String token = jwtUtil.generateToken(user.getUsername(), newRole.name());

        // Evaluate tier if switching to rider mode
        String tierChangeNotification = null;
        if (newRole == User.UserRole.RIDER) {
            LoyaltyTierService.TierEvaluationResult tierResult = loyaltyTierService.evaluateAndUpdateTier(user.getId());
            user = userRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (tierResult.isChanged()) {
                MembershipStatus newTier = tierResult.getNewTier();
                MembershipStatus oldTier = tierResult.getPreviousTier();
                
                if (isTierUpgrade(oldTier, newTier)) {
                    tierChangeNotification = String.format("Congratulations! You've been upgraded to %s tier!", 
                            newTier.name());
                } else {
                    tierChangeNotification = String.format("Your tier has changed from %s to %s based on your recent activity.", 
                            oldTier.name(), newTier.name());
                }
            }
        }

        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getFullName(),
                newRole.name(),
                user.getId(),
                user.getMembershipStatus(),
                tierChangeNotification,
                true, // hasDualRole
                user.getRole().name() // primaryRole
        );
    }

    /**
     * Get current user's account information including flex dollars and tier.
     */
    public UserAccountDTO getCurrentUserAccount() {
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            throw new IllegalArgumentException("User not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User.UserRole activeRole = user.getActiveRole() != null ? user.getActiveRole() : user.getRole();
        boolean hasDualRole = user.getRole() == User.UserRole.OPERATOR;

        return UserAccountDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .activeRole(activeRole)
                .hasDualRole(hasDualRole)
                .tier(user.getMembershipStatus())
                .flexDollars(user.getFlexDollars() != null ? user.getFlexDollars() : java.math.BigDecimal.ZERO)
                .pendingBalance(user.getPendingBalance() != null ? user.getPendingBalance() : java.math.BigDecimal.ZERO)
                .build();
    }

    /**
     * Get tier progress information for the current user.
     */
    public com.qwikride.dto.TierProgressDTO getTierProgress() {
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            throw new IllegalArgumentException("User not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return loyaltyTierService.getTierProgress(user.getId());
    }
}
