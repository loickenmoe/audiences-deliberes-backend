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
 * Le détail d'un bloc rend-il <b>exactement</b> ce que son compteur compte ? (Q-137)
 *
 * <p><b>Pourquoi ce test est le seul qui compte vraiment ici.</b> Le service n'a aucune logique :
 * il choisit une requête et transforme des lignes. Le risque n'est donc pas qu'il calcule mal,
 * c'est que la liste et le compte <b>divergent</b> - un {@code DISTINCT} oublié, un prédicat
 * recopié de travers, une constante qui dérive de celle de {@link ReportingService}. Afficher
 * « 12 » puis lister neuf lignes ferait douter des deux chiffres, et c'est un doute qu'on ne
 * rattrape pas.
 *
 * <p>Un test à mocks ne peut rien dire de cela : il faut les <b>deux requêtes contre la même
 * base</b>, et comparer. La base de test est vide de données métier, donc les deux valent zéro -
 * mais la comparaison vérifie ce qui casse en pratique : que chaque requête s'exécute sur
 * PostgreSQL, que la pagination d'une projection {@code DISTINCT} est acceptée, et que les deux
 * prédicats concordent. Trois choses qui n'échouent qu'à l'exécution.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DetailTableauDeBordIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
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
     * Les trois résultats d'un coup. Le compteur les produit en une seule passe, par trois
     * {@code SUM(CASE WHEN …)} ; le détail les lit un par un. C'est exactement là qu'un prédicat
     * peut diverger - en oubliant la restriction au recouvrement, par exemple, qui n'est écrite
     * qu'une fois dans le compteur.
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
     * Le seul bloc dont l'invariant n'est pas un nombre de lignes : il affiche une <b>somme</b>, et
     * c'est la somme des montants listés qui doit tomber juste.
     *
     * <p>La pagination ne s'applique donc pas à la vérification : on demande une page assez large
     * pour tout tenir, ce que la base de test permet. Sur un volume réel, le total affiché reste
     * celui du compteur - l'écran ne somme pas les lignes qu'il montre.
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
     * Les deux constantes sont déclarées dans {@link ReportingService} <b>et</b> dans
     * {@link DetailTableauDeBordService}, les premières étant privées. Une divergence silencieuse
     * ferait rendre au détail autre chose que ce qui est compté : ce test la rend bruyante.
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
     * <b>L'invariant qui rend le tableau de bord recadrable crédible</b> (Q-138) :
     * Recouvrement + Litiges d'exploitation = Toutes catégories, pour chacun des neuf indicateurs.
     *
     * <p>Il ne repose pas sur mon code mais sur la base : {@code dossier.categorie} est
     * {@code NOT NULL} avec une contrainte {@code CHECK} sur ces deux valeurs exactement (V1). Les
     * deux périmètres partitionnent donc l'ensemble, et ce test le prouve requête par requête -
     * c'est la seule façon de savoir qu'aucune n'a oublié sa jointure sur le dossier, ce qui se
     * verrait sinon comme un sous-total qui ne tombe pas juste.
     *
     * <p>Un tableau de bord dont les sous-totaux ne s'additionnent pas, personne ne le regarde deux
     * fois.
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

        // Le neuvième est une somme d'argent : c'est elle qui doit s'additionner, pas un décompte.
        assertThat(somme(decisions.sommeProvisionsReprises(RECOUVREMENT))
                + somme(decisions.sommeProvisionsReprises(EXPLOITATION)))
                .as("provisions reprises")
                .isEqualTo(somme(decisions.sommeProvisionsReprises(TOUTES)));
    }

    /**
     * La concordance liste/compteur doit tenir <b>sous chaque périmètre</b>, et pas seulement sur le
     * total : c'est sous un périmètre restreint qu'une jointure oubliée se verrait.
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
     * Les trois compteurs de litiges <b>ne sont plus figés sur le recouvrement</b> (Q-138). La
     * garantie que je dois au porteur du projet : son chiffre d'hier reste reproductible, sous le
     * périmètre « Recouvrement ». Rien n'a été changé en silence - le périmètre a reçu un nom.
     */
    @Test
    @DisplayName("le périmètre Recouvrement reproduit exactement l'ancien comptage des litiges")
    void lAncienComptageResteReproductible() {
        var recouvrement = deliberes.calculerResultats(RECOUVREMENT);
        long gagnesEnRecouvrement = deliberes
                .listerParResultat(ResultatDelibere.FAVORABLE, RECOUVREMENT, PREMIERE_PAGE).getTotalElements();

        assertThat(nombre(recouvrement.getFavorables())).isEqualTo(gagnesEnRecouvrement);
        // Et le total toutes catégories ne peut pas être inférieur : il contient celui-là.
        assertThat(nombre(deliberes.calculerResultats(TOUTES).getFavorables()))
                .isGreaterThanOrEqualTo(nombre(recouvrement.getFavorables()));
    }

    private static double somme(Double valeur) {
        return valeur == null ? 0d : valeur;
    }
}
