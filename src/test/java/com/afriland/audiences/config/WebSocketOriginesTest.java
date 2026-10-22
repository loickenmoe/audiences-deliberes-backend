package com.afriland.audiences.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real-time endpoint {@code /ws} accepts the same origins as the REST API
 * ({@code cors.allowed-origins}): the frontend connects, a page from another site is refused at the
 * SockJS handshake, before any STOMP frame. A same-origin request stays accepted.
 */
@SpringJUnitWebConfig(WebSocketOriginesTest.Contexte.class)
@TestPropertySource(properties = "cors.allowed-origins=https://audiences.example.cm,http://localhost:3000")
class WebSocketOriginesTest {

    @Configuration
    @Import({WebSocketConfig.class, WebSocketAuthInterceptor.class})
    static class Contexte {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired private WebApplicationContext contexte;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexte).build();
    }

    @Test
    void frontendAutorise_peutOuvrirLaConnexion() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "https://audiences.example.cm"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://audiences.example.cm"));
    }

    @Test
    void secondeOrigineDeLaListe_estAussiAcceptee() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk());
    }

    @Test
    void siteInconnu_estRefuse() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "https://site-malveillant.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requeteSansOrigine_resteAcceptee() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk());
    }
}
