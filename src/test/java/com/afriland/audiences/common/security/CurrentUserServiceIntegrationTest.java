package com.afriland.audiences.common.security;

import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration test against a real PostgreSQL (Testcontainers), migrated by Flyway as in production. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CurrentUserServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ProfilRepository profilRepository;

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        // Provisioning lives in its own REQUIRES_NEW bean, instantiated directly here: transaction
        // propagation is covered by UtilisateurProvisioningServiceIntegrationTest.
        currentUserService = new CurrentUserService(utilisateurRepository,
                new UtilisateurProvisioningService(utilisateurRepository, profilRepository));
    }

    @Test
    void firstRequestProvisionsAUtilisateurRowWithTheCorrectProfilAgainstTheRealSchema() {
        Jwt jwt = jwt("kc-integration-1", "JURISTE");

        Utilisateur result = currentUserService.resolveOrProvision(jwt);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getProfil().getCode()).isEqualTo("JURISTE");
        assertThat(utilisateurRepository.findByKeycloakId("kc-integration-1")).isPresent();
    }

    @Test
    void secondRequestForTheSameKeycloakIdReturnsTheSameRowWithoutDuplicating() {
        Jwt jwt = jwt("kc-integration-2", "DJ");

        Utilisateur first = currentUserService.resolveOrProvision(jwt);
        Utilisateur second = currentUserService.resolveOrProvision(jwt);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void allSevenReferenceProfilsAreSeededByTheV2Migration() {
        assertThat(profilRepository.findAll()).hasSize(7);
        assertThat(profilRepository.findByCode("AVOCAT")).isPresent();
    }

    private Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(subject)
                .claim("roles", List.of("ROLE_" + role, "ROLE_CONSULTATION"))
                .claim("given_name", "Test")
                .claim("family_name", "Integration")
                .claim("email", subject + "@afriland.cm")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
