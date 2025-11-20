package com.qwikride.service;

import com.qwikride.dto.LoginRequestDTO;
import com.qwikride.dto.LoginResponseDTO;
import com.qwikride.model.User;
import com.qwikride.repository.UserRepository;
import com.qwikride.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoyaltyTierService loyaltyTierService;

    private JwtUtil jwtUtil;

    private AuthenticationService authenticationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.jwtUtil = new JwtUtil();
        this.authenticationService = new AuthenticationService(userRepository, passwordEncoder, jwtUtil, loyaltyTierService);
    }

    @Test
    void authenticate_ValidCredentials_ReturnsToken() {
        // Given
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername("johndoe");
        dto.setPassword("password123");

        User user = new User();
        user.setUsername("johndoe");
        user.setFullName("John Doe");
        user.setPasswordHash("hashedPassword");
        user.setRole(User.UserRole.RIDER);
        user.setMembershipStatus(com.qwikride.prc.domain.MembershipStatus.ENTRY);

        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(loyaltyTierService.evaluateAndUpdateTier(any())).thenReturn(
            new LoyaltyTierService.TierEvaluationResult(
                com.qwikride.prc.domain.MembershipStatus.ENTRY, false, com.qwikride.prc.domain.MembershipStatus.ENTRY
            )
        );
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        // When
        LoginResponseDTO response = authenticationService.authenticate(dto);

        // Then
        assertNotNull(response);
        assertNotNull(response.getToken());
        // Validate token produced by real JwtUtil
        assertTrue(jwtUtil.validateToken(response.getToken(), "johndoe"));
        assertEquals("johndoe", response.getUsername());
        assertEquals("John Doe", response.getFullName());
        assertEquals("RIDER", response.getRole());
    }

    @Test
    void authenticate_InvalidUsername_ThrowsException() {
        // Given
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername("nonexistent");
        dto.setPassword("password123");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> authenticationService.authenticate(dto));
    }

    @Test
    void authenticate_InvalidPassword_ThrowsException() {
        // Given
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername("johndoe");
        dto.setPassword("wrongpassword");

        User user = new User();
        user.setPasswordHash("hashedPassword");

        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "hashedPassword")).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> authenticationService.authenticate(dto));
    }
}