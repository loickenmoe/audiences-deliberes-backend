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
                DetailTableauDeBordService.ETAPES_FERMEES);
        long listees = etapes.listerDossiersAvecEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("affaires vidées : autant de lignes que le compteur annonce")
    void affairesVidees() {
        long compte = etapes.countDossiersSansEtapeEnCours(DetailTableauDeBordService.ETAPES_FERMEES);
        long listees = etapes.listerDossiersSansEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("audiences planifiées : autant de lignes que le compteur annonce")
    void audiencesPlanifiees() {
        long compte = audiences.countByStatut(StatutAudience.PLANIFIEE);
        long listees = audiences.findByStatutOrderByDatePlanifieeAsc(
                StatutAudience.PLANIFIEE, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("frais en attente : autant de lignes que le compteur annonce")
    void fraisEnAttente() {
        long compte = frais.countByStatutCircuitNotIn(DetailTableauDeBordService.FRAIS_SOLDES);
        long listees = frais.findByStatutCircuitNotInOrderByCreatedAtAsc(
                DetailTableauDeBordService.FRAIS_SOLDES, PREMIERE_PAGE).getTotalElements();

        assertThat(listees).isEqualTo(compte);
    }

    @Test
    @DisplayName("alertes de prorogation : autant de lignes que le compteur annonce")
    void prorogations() {
        long compte = alertes.countByTypeAndStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE);
        long listees = alertes.findByTypeAndStatutOrderByIdDesc(
                TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, PREMIERE_PAGE).getTotalElements();

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
        var performance = deliberes.calculerTauxSuccesRecouvrement();

        assertThat(deliberes.listerParResultatEnRecouvrement(ResultatDelibere.FAVORABLE, PREMIERE_PAGE)
                .getTotalElements()).isEqualTo(nombre(performance.getFavorables()));
        assertThat(deliberes.listerParResultatEnRecouvrement(ResultatDelibere.DEFAVORABLE, PREMIERE_PAGE)
                .getTotalElements()).isEqualTo(nombre(performance.getDefavorables()));
        assertThat(deliberes.listerParResultatEnRecouvrement(ResultatDelibere.MIXTE, PREMIERE_PAGE)
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
        double somme = decisions.sommeProvisionsReprises() == null
                ? 0d
                : decisions.sommeProvisionsReprises();
        double sommeDesLignes = decisions.listerProvisionsReprises(PageRequest.of(0, 1_000))
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
}
