package com.afriland.audiences.config;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /** Same origins as the REST API (see {@link SecurityConfig#corsConfigurationSource()}). */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker: "/topic" for publish-subscribe, "/queue" for point-to-point.
        config.enableSimpleBroker("/topic", "/queue");

        // "/app" prefix routes messages to @MessageMapping controller methods.
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations, e.g. "/user/queue/reply".
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        /*
         * "/ws" endpoint with SockJS fallback for browsers without WebSocket support. Only the
         * frontend origins allowed for the REST API may open a connection: a page from another site
         * is refused at the handshake, before the STOMP authentication even runs.
         */
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
