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
 * Authentifie les connexions WebSocket (STOMP) avec les jetons JWT émis par Keycloak.
 * <p>
 * La négociation HTTP/SockJS de l'endpoint {@code /ws/**} reste publique (voir
 * {@link SecurityConfig}) : les transports de repli SockJS (xhr-polling, etc.) ne permettent pas
 * de fixer un en-tête {@code Authorization} sur la requête HTTP initiale. L'authentification
 * réelle est donc appliquée ici, au niveau de la trame STOMP {@code CONNECT}, qui porte l'en-tête
 * {@code Authorization} envoyé par le client STOMP. Toute trame ultérieure (SUBSCRIBE, SEND, ...)
 * émise par une session qui n'a pas obtenu de principal authentifié à la connexion est rejetée.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
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
            // Toute trame autre que CONNECT doit provenir d'une session déjà authentifiée.
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
