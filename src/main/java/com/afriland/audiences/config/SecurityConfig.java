package com.afriland.audiences.config;


import com.afriland.audiences.service.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Import HttpMethod
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Import for method security
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // For CSRF disabling
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration; // Import CORS config
import org.springframework.web.cors.CorsConfigurationSource; // Import CORS source
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // Import CORS source implementation

import java.util.Arrays; // Import Arrays for list creation
import java.util.List; // Import List

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS; // For JWT

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enable method-level security (for @PreAuthorize)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter; // Inject the JWT filter

    // Define constants for Swagger paths
    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable and configure CORS
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF (common for stateless APIs)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll() // Allow auth endpoints
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers("/ws/**").permitAll() // Allow WebSocket handshake/SockJS endpoint
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Explicitly allow ADMIN access to user endpoints here for diagnostics (Can be removed if @PreAuthorize works)
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/prescriptions/**").hasRole("USER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/prescriptions/**").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/prescriptions/**").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/prescriptions").hasRole("USER")
                        .anyRequest().authenticated() // Require authentication for all other requests
                )
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS)) // Use stateless sessions for JWT
                .authenticationProvider(authenticationProvider()) // Set the custom authentication provider
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // Add JWT filter before standard auth filter

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService); // Set the custom user details service
        authProvider.setPasswordEncoder(passwordEncoder()); // Set the password encoder
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // Standard way to get the AuthenticationManager
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Use BCrypt for password hashing
    }

    // --- CORS Configuration Bean ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow requests from the Angular frontend origin
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://127.0.0.1:55235"));
        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Allow common headers
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        // Allow credentials (like cookies or auth tokens)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this configuration to all paths
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // --- Bean to remove the default ROLE_ prefix for hasRole checks (optional, for troubleshooting) ---
    // Re-commented as User model provides ROLE_ prefix correctly.
    /*
    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults(""); // Remove the ROLE_ prefix
    }
    */
}
