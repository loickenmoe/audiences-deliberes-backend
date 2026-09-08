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
 * M15 — non-régression de **R-06** : jusqu'ici, le provisionnement d'un utilisateur à sa première
 * requête échouait (`cannot execute INSERT in a read-only transaction`, 500) dès lors que cette
 * requête tombait sur un endpoint en lecture seule — cas de figure quasi systématique pour un
 * frontend, qui charge notifications et listes juste après le login.
 * <p>
 * Ce test reproduit exactement ce contexte : un appelant annoté
 * {@code @Transactional(readOnly = true)} invoque {@code getCurrentUser()} pour un utilisateur
 * inconnu, contre un PostgreSQL réel (le mode lecture seule n'est refusé que par la base — un test
 * sur base mémoire ou avec repository mocké ne l'aurait jamais détecté, raison pour laquelle le
 * défaut a survécu à 8 jalons).
 */
@SpringBootTest
@Testcontainers
@org.springframework.context.annotation.Import(ProvisionnementLectureSeuleIntegrationTest.AppelantLectureSeule.class)
class ProvisionnementLectureSeuleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
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

    /** Reproduit un service métier en lecture seule (ex. {@code AlerteService.mesNotifications}). */
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
