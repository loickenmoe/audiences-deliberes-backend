package com.afriland.audiences.config;


import lombok.RequiredArgsConstructor;
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
        // "/ws" endpoint with SockJS fallback for browsers without WebSocket support.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // TODO: restrict origins in production
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
