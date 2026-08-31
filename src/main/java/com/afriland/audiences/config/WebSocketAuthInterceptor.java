package com.afriland.audiences.config;


import com.afriland.audiences.service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j // Optional: for logging
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Check if it's a CONNECT command with headers
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract Authorization header (Spring automatically maps 'Authorization' to nativeHeaders)
            List<String> authorization = accessor.getNativeHeader("Authorization");
            log.debug("Authorization header: {}", authorization); // Log header

            String authToken = null;
            if (authorization != null && !authorization.isEmpty()) {
                String authHeader = authorization.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authToken = authHeader.substring(7);
                }
            }

            if (authToken != null) {
                try {
                    String userEmail = jwtService.extractUsername(authToken); // Extracts email (subject)
                    if (userEmail != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail); // Load by email
                        if (jwtService.isTokenValid(authToken, userDetails)) {
                            // Create authentication token
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            // Set the user in the STOMP session headers
                            // This makes it available via headerAccessor.getUser() later
                            accessor.setUser(authentication);
                            log.debug("Authenticated WebSocket user: {}", userEmail);
                        } else {
                            log.warn("Invalid JWT token received in WebSocket connect header.");
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing JWT token in WebSocket connect header: {}", e.getMessage());
                    // Optionally deny connection explicitly? For now, let it proceed unauthenticated.
                }
            } else {
                log.warn("No Authorization Bearer token found in WebSocket connect header.");
            }
        }
        // For other commands (SUBSCRIBE, SEND, etc.), the established security context should be used.
        // If accessor.getUser() is set, Spring uses it. If not, it might check SecurityContextHolder.

        return message;
    }
}