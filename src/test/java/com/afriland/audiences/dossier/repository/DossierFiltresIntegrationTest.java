package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.service.DossierSpecifications;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les filtres par étape et par période d'audience de {@code GET /dossiers} (observation métier du
 * 2026-09-25), sur un PostgreSQL réel.
 *
 * <p><b>Pourquoi pas un test à mocks</b>, comme {@code DossierSpecificationsTest} ? Parce que ces
 * deux filtres sont des sous-requêtes {@code EXISTS}, et qu'un mock ne pourrait vérifier que l'appel
 * à {@code cb.exists(...)} — jamais qu'un dossier corrélé correctement en ressort, ni surtout que le
 * <b>total paginé</b> reste juste. Or c'est précisément là que la jointure naïve échoue : elle
 * compte un dossier autant de fois qu'il a d'étapes. Ce test-là ne se fait qu'en base.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DossierFiltresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_filtres")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DossierRepository dossierRepository;
    @Autowired private EtapeProcedureRepository etapeRepository;
    @Autowired private AudienceRepository audienceRepository;
    @Autowired private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void setUp() {
        audienceRepository.deleteAllInBatch();
        etapeRepository.deleteAllInBatch();
        dossierRepository.deleteAllInBatch();
        clientRepository.deleteAllInBatch();
        client = clientRepository.saveAndFlush(
                Client.builder().reference("CLI-FILTRES").nom("Société de test").build());
    }

    // ------------------------------------------------------------------ Le filtre par étape

    @Test
    void le_type_et_le_statut_portent_sur_une_meme_etape() {
        // Le piège : ce dossier a bien un appel, et il a bien une étape en cours — mais pas la même.
        Dossier leurre = dossier("DOS-LEURRE");
        etape(leurre, TypeEtape.RECOURS_1, StatutCycleVie.CLOTURE);
        etape(leurre, TypeEtape.INSTANCE, StatutCycleVie.EN_COURS);

        Dossier attendu = dossier("DOS-APPEL-EN-COURS");
        etape(attendu, TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS);

        assertThat(references(TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS, null, null))
                .containsExactly("DOS-APPEL-EN-COURS");
    }

    @Test
    void le_type_seul_retient_le_dossier_quel_que_soit_le_statut_de_l_etape() {
        Dossier appel = dossier("DOS-APPEL");
        etape(appel, TypeEtape.RECOURS_1, StatutCycleVie.CLOTURE);
        Dossier premiereInstance = dossier("DOS-INSTANCE");
        etape(premiereInstance, TypeEtape.INSTANCE, StatutCycleVie.EN_COURS);

        assertThat(references(TypeEtape.RECOURS_1, null, null, null)).containsExactly("DOS-APPEL");
    }

    /**
     * Les deux cassations sont concurrentes : un dossier porté devant la Cour Suprême <b>et</b>
     * devant la CCJA remonte dans les deux filtres. C'est exact — les deux procédures existent —
     * et c'est ce qu'aurait caché un filtre portant sur « l'étape la plus avancée », puisqu'il
     * aurait fallu élire l'une des deux.
     */
    @Test
    void un_dossier_aux_deux_cassations_remonte_dans_les_deux_filtres() {
        Dossier deuxCassations = dossier("DOS-DEUX-CASSATIONS");
        etape(deuxCassations, TypeEtape.RECOURS_2, StatutCycleVie.EN_DELIBERE);
        etape(deuxCassations, TypeEtape.RECOURS_2_CCJA, StatutCycleVie.EN_COURS);

        assertThat(references(TypeEtape.RECOURS_2, null, null, null)).containsExactly("DOS-DEUX-CASSATIONS");
        assertThat(references(TypeEtape.RECOURS_2_CCJA, null, null, null)).containsExactly("DOS-DEUX-CASSATIONS");
    }

    /** Le total paginé est la raison d'être du {@code EXISTS} : une jointure compterait trois fois. */
    @Test
    void un_dossier_a_plusieurs_etapes_n_est_compte_qu_une_fois() {
        Dossier dossier = dossier("DOS-TROIS-ETAPES");
        etape(dossier, TypeEtape.INSTANCE, StatutCycleVie.EN_COURS);
        etape(dossier, TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS);
        etape(dossier, TypeEtape.RECOURS_2, StatutCycleVie.EN_COURS);

        Page<Dossier> page = page(null, StatutCycleVie.EN_COURS, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }

    // ------------------------------------------------------------------ Le filtre par période

    @Test
    void la_periode_retient_les_dossiers_ayant_une_audience_dans_la_fenetre() {
        Dossier dansLaFenetre = dossier("DOS-DANS");
        audience(dansLaFenetre, LocalDate.of(2026, 3, 15), StatutAudience.TENUE);
        Dossier horsFenetre = dossier("DOS-HORS");
        audience(horsFenetre, LocalDate.of(2026, 5, 2), StatutAudience.TENUE);

        assertThat(references(null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .containsExactly("DOS-DANS");
    }

    /** Les deux bornes sont incluses : une audience tenue le premier jour est dans la période. */
    @Test
    void les_bornes_de_la_periode_sont_incluses() {
        Dossier debut = dossier("DOS-DEBUT");
        audience(debut, LocalDate.of(2026, 3, 1), StatutAudience.TENUE);
        Dossier fin = dossier("DOS-FIN");
        audience(fin, LocalDate.of(2026, 3, 31), StatutAudience.TENUE);

        assertThat(references(null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .containsExactlyInAnyOrder("DOS-DEBUT", "DOS-FIN");
    }

    /**
     * Une audience annulée ne s'est pas tenue. La compter ferait remonter, dans « les dossiers qui
     * ont eu une audience en mars », des dossiers restés muets tout le mois.
     */
    @Test
    void une_audience_annulee_ne_fait_pas_remonter_le_dossier() {
        Dossier annulee = dossier("DOS-ANNULEE");
        audience(annulee, LocalDate.of(2026, 3, 15), StatutAudience.ANNULEE);

        assertThat(references(null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))).isEmpty();
    }

    @Test
    void une_borne_seule_ouvre_la_periode_de_l_autre_cote() {
        Dossier ancienne = dossier("DOS-ANCIENNE");
        audience(ancienne, LocalDate.of(2026, 1, 10), StatutAudience.TENUE);
        Dossier recente = dossier("DOS-RECENTE");
        audience(recente, LocalDate.of(2026, 6, 10), StatutAudience.TENUE);

        assertThat(references(null, null, LocalDate.of(2026, 3, 1), null)).containsExactly("DOS-RECENTE");
        assertThat(references(null, null, null, LocalDate.of(2026, 3, 1))).containsExactly("DOS-ANCIENNE");
    }

    @Test
    void un_dossier_a_plusieurs_audiences_dans_la_fenetre_n_est_compte_qu_une_fois() {
        Dossier dossier = dossier("DOS-TROIS-AUDIENCES");
        audience(dossier, LocalDate.of(2026, 3, 5), StatutAudience.TENUE);
        audience(dossier, LocalDate.of(2026, 3, 12), StatutAudience.TENUE);
        audience(dossier, LocalDate.of(2026, 3, 19), StatutAudience.TENUE);

        Page<Dossier> page = page(null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Les deux ensemble

    @Test
    void l_etape_et_la_periode_se_cumulent() {
        Dossier lesDeux = dossier("DOS-LES-DEUX");
        etape(lesDeux, TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS);
        audience(lesDeux, LocalDate.of(2026, 3, 15), StatutAudience.TENUE);

        Dossier bonneEtapeSeule = dossier("DOS-ETAPE-SEULE");
        etape(bonneEtapeSeule, TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS);

        Dossier bonnePeriodeSeule = dossier("DOS-PERIODE-SEULE");
        etape(bonnePeriodeSeule, TypeEtape.INSTANCE, StatutCycleVie.EN_COURS);
        audience(bonnePeriodeSeule, LocalDate.of(2026, 3, 15), StatutAudience.TENUE);

        assertThat(references(TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .containsExactly("DOS-LES-DEUX");
    }

    @Test
    void sans_filtre_d_etape_ni_de_periode_tous_les_dossiers_remontent() {
        dossier("DOS-NU-1");
        dossier("DOS-NU-2");

        assertThat(references(null, null, null, null)).containsExactlyInAnyOrder("DOS-NU-1", "DOS-NU-2");
    }

    // ------------------------------------------------------------------ Fabriques

    private List<String> references(TypeEtape type, StatutCycleVie statut, LocalDate debut, LocalDate fin) {
        return page(type, statut, debut, fin).getContent().stream().map(Dossier::getReference).toList();
    }

    private Page<Dossier> page(TypeEtape type, StatutCycleVie statut, LocalDate debut, LocalDate fin) {
        return dossierRepository.findAll(
                DossierSpecifications.withFilters(null, null, null, null, null, null, null, null,
                        type, statut, debut, fin),
                PageRequest.of(0, 20));
    }

    private Dossier dossier(String reference) {
        return dossierRepository.saveAndFlush(Dossier.builder()
                .reference(reference)
                .nature("Assignation")
                .categorie(CategorieDossier.EXPLOITATION_LITIGES)
                .juridictionSaisie("Tribunal de Première Instance de Douala")
                .client(client)
                // `chk_categorie_coherence` (V1) : un dossier d'exploitation-litiges porte au moins
                // l'un des deux champs conditionnels de sa catégorie.
                .elementsJustificatifs("Pièce jointe de test")
                .build());
    }

    private void etape(Dossier dossier, TypeEtape type, StatutCycleVie statut) {
        etapeRepository.saveAndFlush(EtapeProcedure.builder()
                .dossier(dossier)
                .type(type)
                .statutCycleVie(statut)
                .build());
    }

    private void audience(Dossier dossier, LocalDate date, StatutAudience statut) {
        boolean annulee = statut == StatutAudience.ANNULEE;
        audienceRepository.saveAndFlush(Audience.builder()
                .dossier(dossier)
                .datePlanifiee(date)
                .statut(statut)
                // `chk_audience_annulation` (V1) : une annulation porte son motif et sa date.
                .motifAnnulation(annulee ? "Renvoi demandé par la partie adverse" : null)
                .dateAnnulation(annulee ? date.atStartOfDay() : null)
                .build());
    }
}
