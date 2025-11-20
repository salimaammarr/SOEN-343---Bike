package com.qwikride.controller;

import com.qwikride.dto.LoginRequestDTO;
import com.qwikride.dto.LoginResponseDTO;
import com.qwikride.dto.RegistrationRequestDTO;
import com.qwikride.dto.RoleToggleRequestDTO;
import com.qwikride.dto.UserAccountDTO;
import com.qwikride.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        try {
            LoginResponseDTO response = authenticationService.authenticate(dto);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequestDTO dto) {
        try {
            authenticationService.register(dto); 
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (Exception e) { 
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/toggle-role")
    public ResponseEntity<LoginResponseDTO> toggleRole(@Valid @RequestBody RoleToggleRequestDTO dto) {
        try {
            LoginResponseDTO response = authenticationService.toggleRole(dto.getRole());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).build();
        }
    }

    @GetMapping("/account")
    public ResponseEntity<UserAccountDTO> getAccount() {
        try {
            UserAccountDTO account = authenticationService.getCurrentUserAccount();
            return ResponseEntity.ok(account);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
    }
}