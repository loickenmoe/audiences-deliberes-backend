package com.afriland.audiences.model.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@Builder // Lombok: Builder pattern
@NoArgsConstructor // Lombok: No-args constructor
@AllArgsConstructor // Lombok: All-args constructor
@Entity
@Table(name = "_user") // "user" is often a reserved keyword in SQL
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Password cannot be blank")
    // Password length validation should ideally happen before encoding
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank(message = "First name cannot be blank")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    private String lastName;

    @NotNull(message = "Role cannot be null")
    @Enumerated(EnumType.STRING) // Store enum names (ADMIN, USER) in the DB
    @Column(nullable = false)
    private Role role;

    // --- UserDetails Implementation ---

    @Override
    @JsonIgnore // Ignore this during JSON serialization
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return a list containing the user's role as a GrantedAuthority
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name())); // Prefix with ROLE_ for Spring Security convention
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // Spring Security uses username for authentication
        return username;
    }

    @Override
    @JsonIgnore // Ignore this during JSON serialization
    public boolean isAccountNonExpired() {
        return true; // Or add logic for account expiration
    }

    @Override
    @JsonIgnore // Ignore this during JSON serialization
    public boolean isAccountNonLocked() {
        return true; // Or add logic for account locking
    }

    @Override
    @JsonIgnore // Ignore this during JSON serialization
    public boolean isCredentialsNonExpired() {
        return true; // Or add logic for password expiration
    }

    @Override
    @JsonIgnore // Ignore this during JSON serialization
    public boolean isEnabled() {
        return true; // Or add logic for disabling accounts
    }
}