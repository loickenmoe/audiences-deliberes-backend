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
                        // Sidecar Dapr (Q-120) : découverte des abonnements et livraison des accusés
                        // du Notification Engine. Le sidecar partage la pile réseau de l'application
                        // et n'a pas de jeton à présenter ; ces routes ne lisent ni n'exposent aucune
                        // donnée de l'application.
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
     * 401 — jeton absent/invalide, avant même d'atteindre un contrôleur. Format aligné sur
     * {@link com.afriland.audiences.common.error.GlobalExceptionHandler} (REF-07 §1.3).
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeError(
                response, HttpStatus.UNAUTHORIZED, ErrorCodes.NON_AUTHENTIFIE, "Authentification requise");
    }

    /**
     * 403 — droits insuffisants au niveau du filtre (règles URL de authorizeHttpRequests). Les
     * 403 issus d'un {@code @PreAuthorize} au niveau contrôleur sont interceptés séparément par
     * {@link com.afriland.audiences.common.error.GlobalExceptionHandler}.
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
     * Extrait les rôles depuis le claim "roles" du token Keycloak.
     * Le préfixe ROLE_ est déjà inclus dans les noms de rôles Keycloak (ex: ROLE_JURISTE).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix(""); // Les rôles Keycloak ont déjà le préfixe ROLE_

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
         * En-tetes que le navigateur est autorise a **lire** dans la reponse (Q-114).
         *
         * CORS n'expose par defaut qu'une poignee d'en-tetes ; tous les autres existent sur le fil
         * mais restent invisibles au code appelant. Deux d'entre eux portent ici de l'information
         * qui n'est nulle part ailleurs :
         *
         * - `Content-Disposition` : le nom sous lequel l'archive est consignee, reference comprise.
         *   Sans lui, le front reconstruit un nom plausible mais different de celui qu'on citera.
         * - `X-Empreinte-SHA256` : l'empreinte annoncee au telechargement, qui permet de recouper
         *   sans interroger l'application.
         *
         * Defaut trouve en recette de Q-114 : l'ecran affichait « empreinte ... » vide depuis Q-113,
         * l'en-tete etant emis mais illisible.
         */
        config.setExposedHeaders(Arrays.asList("Content-Disposition", "X-Empreinte-SHA256"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
