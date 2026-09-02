package com.afriland.audiences.controller.auth;

import com.afriland.audiences.dto.auth.AuthResponse;
import com.afriland.audiences.dto.auth.LoginRequest;
import com.afriland.audiences.dto.auth.RegisterRequest;
import com.afriland.audiences.service.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Désactivé : l'authentification est gérée par Keycloak
// @RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request // Validate the request body
    ) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (IllegalArgumentException e) {
            // Handle cases like username/email already exists
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            // Catch other potential exceptions during registration
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration failed", e);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request // Validate the request body
    ) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (Exception e) {
             // Handles AuthenticationException and others
             throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login failed: Invalid credentials", e);
        }
    }
}