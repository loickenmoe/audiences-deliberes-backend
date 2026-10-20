package com.afriland.audiences.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * WebSocket authentication is the only authentication path that end-to-end tests do not cover (they
 * use MockMvc/HTTP). Locks the four security decisions of {@link WebSocketAuthInterceptor}: token
 * required, {@code Bearer} scheme required, valid token required, and rejection of any frame sent
 * outside an authenticated session.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        interceptor = new WebSocketAuthInterceptor(jwtDecoder, converter);
    }

    @Test
    void connectAvecJetonValide_authentifieLaSession() {
        Jwt jwt = Jwt.withTokenValue("jeton").header("alg", "none")
                .subject("kc-juriste").claim("roles", List.of("ROLE_JURISTE"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        when(jwtDecoder.decode("jeton")).thenReturn(jwt);

        // The accessor must stay mutable: that is how the inbound STOMP channel builds messages in
        // production, and it lets the interceptor set the authenticated principal.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer jeton");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        assertThat(accessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("kc-juriste");
    }

    @Test
    void connectSansEnteteAuthorization_estRefuse() {
        var trame = trame(StompCommand.CONNECT, null);
        assertThatThrownBy(() -> interceptor.preSend(trame, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorization");
    }

    @Test
    void connectAvecSchemaNonBearer_estRefuse() {
        var trame = trame(StompCommand.CONNECT, "Basic dXNlcjpwYXNz");
        assertThatThrownBy(() -> interceptor.preSend(trame, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectAvecJetonInvalide_estRefuse() {
        when(jwtDecoder.decode("faux")).thenThrow(new JwtException("signature invalide"));

        var trame = trame(StompCommand.CONNECT, "Bearer faux");
        assertThatThrownBy(() -> interceptor.preSend(trame, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void trameNonConnectSansSessionAuthentifiee_estRefusee() {
        var trame = trame(StompCommand.SUBSCRIBE, null);
        assertThatThrownBy(() -> interceptor.preSend(trame, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("non authentifiée");
    }

    @Test
    void trameNonConnectAvecSessionAuthentifiee_passe() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(new JwtAuthenticationToken(
                Jwt.withTokenValue("j").header("alg", "none").subject("kc-juriste")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build(),
                List.of(new SimpleGrantedAuthority("ROLE_JURISTE"))));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isNotNull();
    }

    @Test
    void messageSansAccesseurStomp_estLaissePasser() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private Message<byte[]> trame(StompCommand commande, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(commande);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
