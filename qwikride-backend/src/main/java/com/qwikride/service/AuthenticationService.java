package com.qwikride.service;

import com.qwikride.dto.LoginRequestDTO;
import com.qwikride.dto.LoginResponseDTO;
import com.qwikride.dto.RegistrationRequestDTO;
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

        // Evaluate and update tier for riders
        String tierChangeNotification = null;
        
        if (user.getRole() == User.UserRole.RIDER) {
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

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        
        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getId(),
                user.getMembershipStatus(),
                tierChangeNotification
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
}
