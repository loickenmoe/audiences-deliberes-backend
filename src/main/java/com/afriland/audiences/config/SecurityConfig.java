package com.afriland.audiences.config;

import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/ws/**", "/actuator/health"
    };

    private final ObjectMapper objectMapper;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Dapr sidecar: subscription discovery and Notification Engine receipts.
                        // The sidecar shares the app's network stack and has no token; these routes
                        // neither read nor expose application data.
                        .requestMatchers("/dapr/subscribe", "/notifications/accuses/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * 401 when the token is missing or invalid, before any controller. Same format as
     * {@link com.afriland.audiences.common.error.GlobalExceptionHandler} (REF-07 §1.3).
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeError(
                response, HttpStatus.UNAUTHORIZED, ErrorCodes.NON_AUTHENTIFIE, "Authentification requise");
    }

    /**
     * 403 from URL rules in the filter chain. 403s from controller {@code @PreAuthorize} are handled
     * by {@link com.afriland.audiences.common.error.GlobalExceptionHandler}.
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeError(
                response, HttpStatus.FORBIDDEN, ErrorCodes.DROITS_INSUFFISANTS, "Droits insuffisants");
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                             String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
    }

    /**
     * Reads roles from the Keycloak token's "roles" claim. Keycloak role names already include the
     * ROLE_ prefix (e.g. ROLE_JURISTE), so no prefix is added.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        /*
         * Response headers the browser may read (CORS exposes only a few by default):
         * - `Content-Disposition`: the archive file name, including its reference.
         * - `X-Empreinte-SHA256`: the file hash, to cross-check without querying the application.
         */
        config.setExposedHeaders(Arrays.asList("Content-Disposition", "X-Empreinte-SHA256"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
