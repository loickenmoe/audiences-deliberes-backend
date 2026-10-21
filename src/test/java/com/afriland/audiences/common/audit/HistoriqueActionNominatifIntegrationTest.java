package com.afriland.audiences.common.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two queries of the per-person report, against PostgreSQL.
 *
 * <p>The service is tested on mocked projections, which say nothing about what the database
 * accepts. One query is <b>native</b> and reads a {@code jsonb} document; PostgreSQL writes key
 * presence as {@code details ? 'audienceId'}, but {@code ?} is the JDBC positional parameter
 * marker. This test guards the use of {@code jsonb_exists}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class HistoriqueActionNominatifIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_nominatif")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private HistoriqueActionRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private static final LocalDateTime DEBUT = LocalDate.of(2026, 9, 1).atStartOfDay();
    private static final LocalDateTime FIN = LocalDate.of(2026, 10, 1).atStartOfDay();

    /**
     * Fixtures are set up in SQL rather than through entities: audit entries are created by domain
     * services, which a repository test does not need to start.
     */
    private long poserUnJuriste(String nom) {
        Long profilId = jdbc.queryForObject(
                "SELECT id FROM profil WHERE code = 'JURISTE'", Long.class);
        jdbc.update("""
                INSERT INTO utilisateur (keycloak_id, nom, prenom, email, profil_id, actif)
                VALUES (?, ?, 'Test', ?, ?, true)
                """, "kc-" + nom, nom, nom + "@afriland.cm", profilId);
        return jdbc.queryForObject("SELECT id FROM utilisateur WHERE nom = ?", Long.class, nom);
    }

    private void poserUneAction(long utilisateurId, TypeActionHistorique code, LocalDateTime quand,
                                String details) {
        jdbc.update("""
                INSERT INTO historique_action (utilisateur_id, code, action, details, date_action)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                """, utilisateurId, code.name(), code.libelle(), details, quand);
    }

    @Test
    void compte_parPersonneEtParNature_surLaPeriodeSeulement() {
        long juriste = poserUnJuriste("Compteur");
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, DEBUT.plusDays(3), null);
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, DEBUT.plusDays(9), null);
        poserUneAction(juriste, TypeActionHistorique.DELIBERE_ENREGISTRE, DEBUT.plusDays(4), null);
        // Out of period: before the lower bound, and at the upper bound, which is exclusive.
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, DEBUT.minusDays(1), null);
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, FIN, null);
        // Untracked action type: must not appear.
        poserUneAction(juriste, TypeActionHistorique.AUDIT_CONSULTE, DEBUT.plusDays(5), null);

        List<HistoriqueActionRepository.ActiviteParPersonne> lignes = repository.compterActivitesParPersonne(
                Set.of(TypeActionHistorique.DOSSIER_CREE, TypeActionHistorique.DELIBERE_ENREGISTRE),
                DEBUT, FIN);

        assertThat(lignes)
                .filteredOn(l -> l.getUtilisateurId().equals(juriste))
                .extracting(l -> l.getCode().name() + "=" + l.getTotal())
                .containsExactlyInAnyOrder("DOSSIER_CREE=2", "DELIBERE_ENREGISTRE=1");
    }

    /**
     * Average entry delay, read through the {@code jsonb}. This query would have failed silently with
     * the {@code ?} operator and can only be exercised here.
     */
    @Test
    void delaiMoyen_litLIdentifiantDAudienceDansLeJsonb() {
        long juriste = poserUnJuriste("Delai");
        long dossierId = poserUnDossier();
        long etapeId = jdbc.queryForObject(
                "SELECT id FROM etape_procedure WHERE dossier_id = ? ORDER BY id LIMIT 1", Long.class, dossierId);

        // Two hearings, two reports entered two and four days late.
        long audienceUn = insererAudience(dossierId, etapeId, LocalDate.of(2026, 9, 10));
        long audienceDeux = insererAudience(dossierId, etapeId, LocalDate.of(2026, 9, 20));
        poserUneAction(juriste, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                LocalDate.of(2026, 9, 12).atStartOfDay(), "{\"audienceId\": " + audienceUn + "}");
        poserUneAction(juriste, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                LocalDate.of(2026, 9, 24).atStartOfDay(), "{\"audienceId\": " + audienceDeux + "}");
        // Details without `audienceId`: ignored rather than skewing the average.
        poserUneAction(juriste, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                LocalDate.of(2026, 9, 25).atStartOfDay(), "{\"autreChose\": 1}");

        List<HistoriqueActionRepository.DelaiSaisieParPersonne> delais =
                repository.delaiMoyenDeSaisieParPersonne(DEBUT, FIN);

        assertThat(delais)
                .filteredOn(d -> d.getUtilisateurId().equals(juriste))
                .singleElement()
                .satisfies(d -> assertThat(d.getDelaiMoyen()).isEqualTo(3.0d));
    }

    /**
     * The minimum for a hearing to exist: a client, a case, a step. The test database only holds
     * migration reference data; inserting by hand keeps the test focused on the two queries.
     */
    private long poserUnDossier() {
        jdbc.update("INSERT INTO client (reference, nom) VALUES ('CLI-TEST-001', 'Client de test')");
        Long clientId = jdbc.queryForObject(
                "SELECT id FROM client WHERE reference = 'CLI-TEST-001'", Long.class);
        jdbc.update("""
                INSERT INTO dossier (reference, nature, categorie, juridiction_saisie, client_id)
                VALUES ('DOS-TEST-001', 'Recouvrement de créances', 'RECOUVREMENT', 'Tribunal de test', ?)
                """, clientId);
        Long dossierId = jdbc.queryForObject(
                "SELECT id FROM dossier WHERE reference = 'DOS-TEST-001'", Long.class);
        jdbc.update("""
                INSERT INTO etape_procedure (dossier_id, type, statut_cycle_vie)
                VALUES (?, 'INSTANCE', 'EN_COURS')
                """, dossierId);
        return dossierId;
    }

    private long insererAudience(long dossierId, long etapeId, LocalDate date) {
        jdbc.update("""
                INSERT INTO audience (dossier_id, etape_id, juridiction, date_planifiee, statut)
                VALUES (?, ?, 'Tribunal de test', ?, 'PLANIFIEE')
                """, dossierId, etapeId, date);
        return jdbc.queryForObject(
                "SELECT id FROM audience WHERE date_planifiee = ? ORDER BY id DESC LIMIT 1", Long.class, date);
    }
}
