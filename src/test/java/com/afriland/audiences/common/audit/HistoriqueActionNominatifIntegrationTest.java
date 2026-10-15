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
 * Les deux requêtes du rapport nominatif, <b>contre PostgreSQL</b> (observation 14, N6).
 *
 * <p><b>Pourquoi une intégration ici, alors que le service est déjà testé.</b> Le service travaille
 * sur des projections simulées : il ne dit rien de ce que la base accepte. Or l'une des deux
 * requêtes est **native** et lit un document {@code jsonb} - deux choses qu'aucun test à mocks ne
 * peut valider, et qui n'échoueraient qu'à l'exécution. Le piège exact : PostgreSQL écrit la
 * présence d'une clé {@code details ? 'audienceId'}, mais {@code ?} est le marqueur de paramètre
 * positionnel de JDBC. C'est ce test qui tient {@code jsonb_exists}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class HistoriqueActionNominatifIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
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
     * Le décor est posé en SQL, et non par les entités : la trace d'audit se crée par les services
     * du domaine, qu'un test de dépôt n'a pas à démarrer pour vérifier une agrégation.
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
        // Hors période : avant la borne basse, et à la borne haute qui est exclue.
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, DEBUT.minusDays(1), null);
        poserUneAction(juriste, TypeActionHistorique.DOSSIER_CREE, FIN, null);
        // Nature non suivie : elle ne doit pas apparaître.
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
     * Le délai moyen de saisie, lu à travers le {@code jsonb}.
     *
     * <p>C'est cette requête qui aurait échoué silencieusement avec l'opérateur {@code ?}, et qui
     * ne pouvait être éprouvée qu'ici.
     */
    @Test
    void delaiMoyen_litLIdentifiantDAudienceDansLeJsonb() {
        long juriste = poserUnJuriste("Delai");
        long dossierId = poserUnDossier();
        long etapeId = jdbc.queryForObject(
                "SELECT id FROM etape_procedure WHERE dossier_id = ? ORDER BY id LIMIT 1", Long.class, dossierId);

        // Deux audiences, deux comptes rendus saisis avec deux et quatre jours de retard.
        long audienceUn = insererAudience(dossierId, etapeId, LocalDate.of(2026, 9, 10));
        long audienceDeux = insererAudience(dossierId, etapeId, LocalDate.of(2026, 9, 20));
        poserUneAction(juriste, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                LocalDate.of(2026, 9, 12).atStartOfDay(), "{\"audienceId\": " + audienceUn + "}");
        poserUneAction(juriste, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                LocalDate.of(2026, 9, 24).atStartOfDay(), "{\"audienceId\": " + audienceDeux + "}");
        // Détails sans `audienceId` : ignoré plutôt que de fausser la moyenne.
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
     * Le strict nécessaire pour qu'une audience puisse exister : un client, un dossier, une étape.
     *
     * <p>La base de test ne porte que le référentiel des migrations - aucun dossier. Les poser à la
     * main plutôt que par les services du domaine garde ce test sur ce qu'il éprouve : deux
     * requêtes, et non une chaîne de création.
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
