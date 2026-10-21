package com.afriland.audiences.common.security;

import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provisioning a user on their first request must not fail when that request hits a read-only
 * endpoint ({@code cannot execute INSERT in a read-only transaction}), the usual case for a frontend
 * loading notifications and lists right after login.
 * <p>
 * A caller annotated {@code @Transactional(readOnly = true)} calls {@code getCurrentUser()} for an
 * unknown user against a real PostgreSQL: only the database enforces read-only mode, so an
 * in-memory or mocked test would not detect the issue.
 */
@SpringBootTest
@Testcontainers
@org.springframework.context.annotation.Import(ProvisionnementLectureSeuleIntegrationTest.AppelantLectureSeule.class)
class ProvisionnementLectureSeuleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_r06")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private io.minio.MinioClient minioClient;

    @Autowired private AppelantLectureSeule appelantLectureSeule;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    void unUtilisateurInconnu_estProvisionneDepuisUnAppelantEnLectureSeule() {
        Jwt jwt = jwt("kc-r06-nouveau", "JURISTE");
        assertThat(utilisateurRepository.findByKeycloakId("kc-r06-nouveau")).isEmpty();

        Utilisateur resolu = appelantLectureSeule.lireLUtilisateurCourant(jwt);

        assertThat(resolu.getId()).isNotNull();
        assertThat(resolu.getProfil().getCode()).isEqualTo("JURISTE");
        assertThat(utilisateurRepository.findByKeycloakId("kc-r06-nouveau")).isPresent();
    }

    @Test
    void unSecondAppelEnLectureSeule_neDupliquePasLaLigne() {
        Jwt jwt = jwt("kc-r06-idempotent", "DJA");

        Utilisateur premier = appelantLectureSeule.lireLUtilisateurCourant(jwt);
        Utilisateur second = appelantLectureSeule.lireLUtilisateurCourant(jwt);

        assertThat(second.getId()).isEqualTo(premier.getId());
        assertThat(utilisateurRepository.findAll().stream()
                .filter(u -> "kc-r06-idempotent".equals(u.getKeycloakId()))).hasSize(1);
    }

    private Jwt jwt(String sujet, String role) {
        return Jwt.withTokenValue("jeton").header("alg", "none")
                .subject(sujet)
                .claim("roles", List.of("ROLE_" + role))
                .claim("given_name", "Nouveau").claim("family_name", "Venu")
                .claim("email", sujet + "@afriland.cm")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    /** Simulates a read-only business service (e.g. {@code AlerteService.mesNotifications}). */
    @Service
    static class AppelantLectureSeule {

        private final CurrentUserService currentUserService;

        AppelantLectureSeule(CurrentUserService currentUserService) {
            this.currentUserService = currentUserService;
        }

        @Transactional(readOnly = true)
        public Utilisateur lireLUtilisateurCourant(Jwt jwt) {
            return currentUserService.resolveOrProvision(jwt);
        }
    }
}
