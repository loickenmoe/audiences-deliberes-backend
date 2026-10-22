package com.afriland.audiences.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates WebSocket (STOMP) connections with Keycloak-issued JWTs.
 * <p>
 * The HTTP/SockJS handshake on {@code /ws/**} stays public (see {@link SecurityConfig}) because
 * SockJS fallback transports cannot set an {@code Authorization} header. Authentication is enforced
 * on the STOMP {@code CONNECT} frame instead; any later frame from an unauthenticated session is
 * rejected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    // Signature imposed by ChannelInterceptor: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            AbstractAuthenticationToken authentication = authenticate(accessor);
            accessor.setUser(authentication);
            log.debug("Connexion WebSocket authentifiée : {}", authentication.getName());
        } else if (accessor.getUser() == null) {
            // Any frame other than CONNECT must come from an already authenticated session.
            log.warn("Trame STOMP {} refusée : aucune session authentifiée.", accessor.getCommand());
            throw new AccessDeniedException("Connexion WebSocket non authentifiée");
        }

        return message;
    }

    private AbstractAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            throw new AccessDeniedException("En-tête Authorization manquant à la connexion WebSocket");
        }

        String authHeader = authorization.get(0);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("En-tête Authorization invalide à la connexion WebSocket");
        }

        String token = authHeader.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return jwtAuthenticationConverter.convert(jwt);
        } catch (JwtException e) {
            throw new AccessDeniedException("Jeton JWT Keycloak invalide à la connexion WebSocket", e);
        }
    }
}
