package com.afriland.audiences.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email cannot be blank") // Update message
    private String email; // Change field name to email

    @NotBlank(message = "Password cannot be blank")
    private String password;
}