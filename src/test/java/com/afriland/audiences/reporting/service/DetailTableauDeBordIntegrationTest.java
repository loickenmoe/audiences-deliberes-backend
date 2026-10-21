package com.afriland.audiences.reporting.service;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Does a block's detail return <b>exactly</b> what its counter counts?
 *
 * <p>The service has no logic: it picks a query and maps rows. The risk is that the list and the
 * count <b>diverge</b> (a forgotten {@code DISTINCT}, a miscopied predicate, a constant drifting from
 * {@link ReportingService}). Only <b>both queries against the same database</b> can show it. The test
 * database has no business data, so both are zero, but the comparison checks what breaks in practice:
 * each query runs on PostgreSQL, {@code DISTINCT} projection pagination is accepted, and predicates
 * match.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DetailTableauDeBordIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_detail_tdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EtapeProcedureRepository etapes;
    @Autowired private AudienceRepository audiences;
    @Autowired private DemandeFraisRepository frais;
    @Autowired private AlerteRepository alertes;
    @Autowired private DelibereRepository deliberes;
    @Autowired private DecisionDefinitiveRepository decisions;

    private static final PageRequest PREMIERE_PAGE = PageRequest.of(0, 20);

    @Test
    @DisplayName("dossiers actifs : autant de lignes que le compteur annonce")
    void dossiersActifs() {
        long compte = etapes.countDistinctDossierIdByStatutCycleVieNotIn(
                DetailTableauDeBordService.ETAPES_FERMEES, TOUTES);
        long listees = etapes.listerDossiersAvecEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, TOUTES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("affaires vidées : autant de lignes que le compteur annonce")
    void affairesVidees() {
        long compte = etapes.countDossiersSansEtapeEnCours(DetailTableauDeBordService.ETAPES_FERMEES, TOUTES);
        long listees = etapes.listerDossiersSansEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, TOUTES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("audiences planifiées : autant de lignes que le compteur annonce")
    void audiencesPlanifiees() {
        long compte = audiences.compterParStatut(StatutAudience.PLANIFIEE, TOUTES);
        long listees = audiences.listerParStatut(
                StatutAudience.PLANIFIEE, TOUTES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("frais en attente : autant de lignes que le compteur annonce")
    void fraisEnAttente() {
        long compte = frais.compterNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, TOUTES);
        long listees = frais.listerNonSoldees(
                DetailTableauDeBordService.FRAIS_SOLDES, TOUTES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("alertes de prorogation : autant de lignes que le compteur annonce")
    void prorogations() {
        long compte = alertes.compterParTypeEtStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, TOUTES);
        long listees = alertes.listerParTypeEtStatut(
                TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, TOUTES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    /**
     * The three results at once. The counter computes them in a single pass with three
     * {@code SUM(CASE WHEN ...)}; the detail reads them one by one. This is where a predicate can diverge
     * (e.g. forgetting the recovery restriction written only once in the counter).
     */
    @Test
    @DisplayName("litiges gagnés, perdus et mixtes : chaque liste tombe sur son décompte")
    void deliberesParResultat() {
        var performance = deliberes.calculerResultats(TOUTES);

        assertThat(deliberes.listerParResultat(ResultatDelibere.FAVORABLE, TOUTES, PREMIERE_PAGE)
                .getTotalElements()).isEqualTo(nombre(performance.getFavorables()));
        assertThat(deliberes.listerParResultat(ResultatDelibere.DEFAVORABLE, TOUTES, PREMIERE_PAGE)
                .getTotalElements()).isEqualTo(nombre(performance.getDefavorables()));
        assertThat(deliberes.listerParResultat(ResultatDelibere.MIXTE, TOUTES, PREMIERE_PAGE)
                .getTotalElements()).isEqualTo(nombre(performance.getMixtes()));
    }

    /**
     * The only block whose invariant is not a row count: it shows a <b>sum</b>, and the sum of listed
     * amounts must match. Pagination does not apply to the check: a page large enough for everything is
     * requested. On real volumes, the displayed total stays the counter's.
     */
    @Test
    @DisplayName("provisions reprises : la somme des montants listés fait le montant affiché")
    void provisionsReprises() {
        double somme = decisions.sommeProvisionsReprises(TOUTES) == null
                ? 0d
                : decisions.sommeProvisionsReprises(TOUTES);
        double sommeDesLignes = decisions.listerProvisionsReprises(TOUTES, PageRequest.of(0, 1_000))
                .getContent().stream()
                .mapToDouble(adjudication -> adjudication.getReliquat() == null ? 0d : adjudication.getReliquat())
                .sum();

        assertThat(sommeDesLignes).isEqualTo(somme);
    }

    /**
     * Both constants are declared in {@link ReportingService} <b>and</b> {@link DetailTableauDeBordService}
     * (the former private). This test makes any divergence loud.
     */
    @Test
    @DisplayName("les constantes du détail sont celles du comptage")
    void memesConstantes() {
        assertThat(DetailTableauDeBordService.ETAPES_FERMEES)
                .containsExactly(com.afriland.audiences.dossier.domain.StatutCycleVie.CLOTURE,
                        com.afriland.audiences.dossier.domain.StatutCycleVie.ARCHIVE);
        assertThat(DetailTableauDeBordService.FRAIS_SOLDES)
                .containsExactly(com.afriland.audiences.frais.domain.StatutCircuitFrais.PAYEE,
                        com.afriland.audiences.frais.domain.StatutCircuitFrais.REJETEE);
    }

    private static long nombre(Long valeur) {
        return valeur == null ? 0L : valeur;
    }

    private static final java.util.List<CategorieDossier> TOUTES =
            java.util.List.of(CategorieDossier.values());
    private static final java.util.List<CategorieDossier> RECOUVREMENT =
            java.util.List.of(CategorieDossier.RECOUVREMENT);
    private static final java.util.List<CategorieDossier> EXPLOITATION =
            java.util.List.of(CategorieDossier.EXPLOITATION_LITIGES);

    /**
     * <b>Invariant:</b> Recovery + Operating litigation = All categories, for each of the nine indicators.
     *
     * <p>It relies on the database: {@code dossier.categorie} is {@code NOT NULL} with a {@code CHECK} on
     * exactly these two values (V1), so both scopes partition the set. Checking it query by query proves
     * none forgot its join on the case, which would show as a subtotal that does not add up.
     */
    @Test
    @DisplayName("les deux catégories s'additionnent pour faire le total, sur les neuf indicateurs")
    void lesDeuxCategoriesFontLeTotal() {
        assertThat(etapes.countDistinctDossierIdByStatutCycleVieNotIn(
                DetailTableauDeBordService.ETAPES_FERMEES, RECOUVREMENT)
                + etapes.countDistinctDossierIdByStatutCycleVieNotIn(
                        DetailTableauDeBordService.ETAPES_FERMEES, EXPLOITATION))
                .as("dossiers actifs")
                .isEqualTo(etapes.countDistinctDossierIdByStatutCycleVieNotIn(
                        DetailTableauDeBordService.ETAPES_FERMEES, TOUTES));

        assertThat(etapes.countDossiersSansEtapeEnCours(DetailTableauDeBordService.ETAPES_FERMEES, RECOUVREMENT)
                + etapes.countDossiersSansEtapeEnCours(DetailTableauDeBordService.ETAPES_FERMEES, EXPLOITATION))
                .as("affaires vidées")
                .isEqualTo(etapes.countDossiersSansEtapeEnCours(
                        DetailTableauDeBordService.ETAPES_FERMEES, TOUTES));

        assertThat(audiences.compterParStatut(StatutAudience.PLANIFIEE, RECOUVREMENT)
                + audiences.compterParStatut(StatutAudience.PLANIFIEE, EXPLOITATION))
                .as("audiences planifiées")
                .isEqualTo(audiences.compterParStatut(StatutAudience.PLANIFIEE, TOUTES));

        assertThat(frais.compterNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, RECOUVREMENT)
                + frais.compterNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, EXPLOITATION))
                .as("frais en attente")
                .isEqualTo(frais.compterNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, TOUTES));

        assertThat(alertes.compterParTypeEtStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, RECOUVREMENT)
                + alertes.compterParTypeEtStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, EXPLOITATION))
                .as("alertes de prorogation")
                .isEqualTo(alertes.compterParTypeEtStatut(
                        TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, TOUTES));

        for (ResultatDelibere resultat : ResultatDelibere.values()) {
            assertThat(deliberes.listerParResultat(resultat, RECOUVREMENT, PREMIERE_PAGE).getTotalElements()
                    + deliberes.listerParResultat(resultat, EXPLOITATION, PREMIERE_PAGE).getTotalElements())
                    .as("délibérés %s", resultat)
                    .isEqualTo(deliberes.listerParResultat(resultat, TOUTES, PREMIERE_PAGE).getTotalElements());
        }

        // The ninth is an amount of money: it must add up, not a count.
        assertThat(somme(decisions.sommeProvisionsReprises(RECOUVREMENT))
                + somme(decisions.sommeProvisionsReprises(EXPLOITATION)))
                .as("provisions reprises")
                .isEqualTo(somme(decisions.sommeProvisionsReprises(TOUTES)));
    }

    /**
     * List/counter agreement must hold <b>under each scope</b>, not only on the total: a forgotten join
     * shows under a restricted scope.
     */
    @Test
    @DisplayName("sous chaque périmètre, chaque liste tombe encore sur son compteur")
    void concordanceSousChaquePerimetre() {
        for (var categories : java.util.List.of(TOUTES, RECOUVREMENT, EXPLOITATION)) {
            assertThat(etapes.listerDossiersAvecEtapeEnCours(
                    DetailTableauDeBordService.ETAPES_FERMEES, categories, PREMIERE_PAGE).getTotalElements())
                    .as("dossiers actifs, périmètre %s", categories)
                    .isEqualTo(etapes.countDistinctDossierIdByStatutCycleVieNotIn(
                            DetailTableauDeBordService.ETAPES_FERMEES, categories));

            assertThat(etapes.listerDossiersSansEtapeEnCours(
                    DetailTableauDeBordService.ETAPES_FERMEES, categories, PREMIERE_PAGE).getTotalElements())
                    .as("affaires vidées, périmètre %s", categories)
                    .isEqualTo(etapes.countDossiersSansEtapeEnCours(
                            DetailTableauDeBordService.ETAPES_FERMEES, categories));

            assertThat(audiences.listerParStatut(StatutAudience.PLANIFIEE, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("audiences planifiées, périmètre %s", categories)
                    .isEqualTo(audiences.compterParStatut(StatutAudience.PLANIFIEE, categories));

            assertThat(frais.listerNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("frais en attente, périmètre %s", categories)
                    .isEqualTo(frais.compterNonSoldees(DetailTableauDeBordService.FRAIS_SOLDES, categories));

            assertThat(alertes.listerParTypeEtStatut(
                    TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("alertes de prorogation, périmètre %s", categories)
                    .isEqualTo(alertes.compterParTypeEtStatut(
                            TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, categories));

            var performance = deliberes.calculerResultats(categories);
            assertThat(deliberes.listerParResultat(ResultatDelibere.FAVORABLE, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("litiges gagnés, périmètre %s", categories)
                    .isEqualTo(nombre(performance.getFavorables()));
            assertThat(deliberes.listerParResultat(ResultatDelibere.DEFAVORABLE, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("litiges perdus, périmètre %s", categories)
                    .isEqualTo(nombre(performance.getDefavorables()));
            assertThat(deliberes.listerParResultat(ResultatDelibere.MIXTE, categories, PREMIERE_PAGE)
                    .getTotalElements())
                    .as("décisions mixtes, périmètre %s", categories)
                    .isEqualTo(nombre(performance.getMixtes()));
        }
    }

    /**
     * The three litigation counters are <b>no longer fixed to recovery</b>. The former figure remains
     * reproducible under the "Recouvrement" scope.
     */
    @Test
    @DisplayName("le périmètre Recouvrement reproduit exactement l'ancien comptage des litiges")
    void lAncienComptageResteReproductible() {
        var recouvrement = deliberes.calculerResultats(RECOUVREMENT);
        long gagnesEnRecouvrement = deliberes
                .listerParResultat(ResultatDelibere.FAVORABLE, RECOUVREMENT, PREMIERE_PAGE).getTotalElements();

        assertThat(nombre(recouvrement.getFavorables())).isEqualTo(gagnesEnRecouvrement);
        // The all-categories total cannot be lower: it contains this one.
        assertThat(nombre(deliberes.calculerResultats(TOUTES).getFavorables()))
                .isGreaterThanOrEqualTo(nombre(recouvrement.getFavorables()));
    }

    private static double somme(Double valeur) {
        return valeur == null ? 0d : valeur;
    }
}
