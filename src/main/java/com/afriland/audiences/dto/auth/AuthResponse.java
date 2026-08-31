package com.afriland.audiences.dto.auth;

import com.afriland.audiences.model.security.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token; // The JWT token
    private User user; // Add the user details field
    // Optionally, you could include the refresh token here as well
    // private String refreshToken;
}