package com.afriland.audiences.reporting.service;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import org.junit.jupiter.api.BeforeEach;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private AudienceRepository audienceRepository;
    @Mock private DemandeFraisRepository demandeFraisRepository;
    @Mock private AlerteRepository alerteRepository;
    @Mock private DelibereRepository delibereRepository;
    @Mock private DecisionDefinitiveRepository decisionDefinitiveRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DelibereRepository.PerformanceGlobale performanceGlobale;

    private ReportingService service;

    @BeforeEach
    void setUp() {
        service = new ReportingService(etapeProcedureRepository, audienceRepository, demandeFraisRepository,
                alerteRepository, delibereRepository, decisionDefinitiveRepository, dossierRepository);
    }

    @Test
    void tableauDeBord_aggregatesAllMetrics() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList(), anyList())).thenReturn(12L);
        when(etapeProcedureRepository.countDossiersSansEtapeEnCours(anyList(), anyList())).thenReturn(5L);
        when(audienceRepository.compterParStatut(eq(StatutAudience.PLANIFIEE), anyList())).thenReturn(7L);
        when(demandeFraisRepository.compterNonSoldees(anyList(), anyList())).thenReturn(3L);
        when(alerteRepository.compterParTypeEtStatut(eq(TypeAlerte.PROROGATION), eq(StatutAlerte.DECLENCHEE), anyList())).thenReturn(2L);
        when(delibereRepository.calculerResultats(anyList())).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(3L);
        when(performanceGlobale.getDefavorables()).thenReturn(1L);
        when(performanceGlobale.getMixtes()).thenReturn(0L);
        when(performanceGlobale.getTotal()).thenReturn(4L);
        when(decisionDefinitiveRepository.sommeProvisionsReprises(anyList())).thenReturn(500000d);

        TableauDeBordResponse result = service.tableauDeBord(null);

        assertThat(result.nbDossiersActifs()).isEqualTo(12L);
        assertThat(result.nbAudiencesPlanifiees()).isEqualTo(7L);
        assertThat(result.nbDemandesFraisEnAttente()).isEqualTo(3L);
        assertThat(result.nbProrogationsAlerte()).isEqualTo(2L);
        assertThat(result.tauxSuccesRecouvrement()).isEqualTo(0.75d);
        // Observation 12 : la Direction lisait un taux sans savoir sur combien d'affaires il portait.
        assertThat(result.nbLitigesGagnes()).isEqualTo(3L);
        assertThat(result.nbLitigesPerdus()).isEqualTo(1L);
        assertThat(result.nbLitigesMixtes()).isZero();
        /*
         * « Une affaire est vidée lorsqu'elle est à l'étape clôturée » (métier, 2026-10-06). Un
         * dossier portant plusieurs étapes, la règle se lit au niveau du dossier : plus aucune
         * étape en cours. C'est le complément exact des dossiers actifs, affiché à côté.
         */
        assertThat(result.nbAffairesVidees()).isEqualTo(5L);
        assertThat(result.provisionsReprises()).isEqualTo(500000d);
    }

    /**
     * « Perdus » n'est <b>pas</b> « total moins gagnés » (métier, 2026-10-03, observation 12).
     *
     * <p>{@code ResultatDelibere} a trois valeurs : un délibéré {@code MIXTE} n'est ni un succès ni
     * un échec. Le déduire du total aurait compté les affaires partiellement gagnées comme des
     * pertes, et la Direction aurait lu un chiffre faux sans moyen de s'en apercevoir.
     */
    @Test
    void tableauDeBord_neCompteNiGagneNiPerduUnDelibereMixte() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList(), anyList())).thenReturn(0L);
        when(etapeProcedureRepository.countDossiersSansEtapeEnCours(anyList(), anyList())).thenReturn(0L);
        when(audienceRepository.compterParStatut(any(), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterNonSoldees(anyList(), anyList())).thenReturn(0L);
        when(alerteRepository.compterParTypeEtStatut(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultats(anyList())).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(4L);
        when(performanceGlobale.getDefavorables()).thenReturn(2L);
        when(performanceGlobale.getMixtes()).thenReturn(4L);
        when(performanceGlobale.getTotal()).thenReturn(10L);
        when(decisionDefinitiveRepository.sommeProvisionsReprises(anyList())).thenReturn(null);

        TableauDeBordResponse result = service.tableauDeBord(null);

        assertThat(result.nbLitigesGagnes()).isEqualTo(4L);
        assertThat(result.nbLitigesPerdus()).isEqualTo(2L);
        assertThat(result.nbLitigesMixtes()).isEqualTo(4L);
        // Les trois s'additionnent au total : rien n'est perdu de vue, rien n'est attribué d'office.
        assertThat(result.nbLitigesGagnes() + result.nbLitigesPerdus() + result.nbLitigesMixtes())
                .isEqualTo(10L);
    }

    @Test
    void tableauDeBord_defaultsTauxToZeroWhenNoDelibere() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList(), anyList())).thenReturn(0L);
        when(etapeProcedureRepository.countDossiersSansEtapeEnCours(anyList(), anyList())).thenReturn(0L);
        when(audienceRepository.compterParStatut(any(), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterNonSoldees(anyList(), anyList())).thenReturn(0L);
        when(alerteRepository.compterParTypeEtStatut(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultats(anyList())).thenReturn(performanceGlobale);
        // `SUM` rend `null` sur un ensemble vide, non zéro : les trois décomptes doivent y survivre.
        when(performanceGlobale.getFavorables()).thenReturn(null);
        when(performanceGlobale.getDefavorables()).thenReturn(null);
        when(performanceGlobale.getMixtes()).thenReturn(null);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprises(anyList())).thenReturn(null);

        TableauDeBordResponse result = service.tableauDeBord(null);

        assertThat(result.tauxSuccesRecouvrement()).isZero();
        assertThat(result.provisionsReprises()).isZero();
    }

    @Test
    void genererRapport_scopesToPeriodAndDetectsEmpty() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        when(dossierRepository.compterCreesEntre(any(), any(), anyList())).thenReturn(0L);
        when(audienceRepository.compterTenuesEntre(eq(StatutAudience.TENUE), eq(debut), eq(fin), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterDeposeesEntre(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.compterRendusEntre(eq(debut), eq(fin), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultatsPeriode(eq(debut), eq(fin), anyList())).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(0L);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(eq(debut), eq(fin), anyList())).thenReturn(0d);

        RapportResponse result = service.genererRapport(TypeRapport.MENSUEL, debut, fin, null);

        assertThat(result.estVide()).isTrue();
        assertThat(result.type()).isEqualTo(TypeRapport.MENSUEL);
    }

    @Test
    void genererRapport_nonEmptyWhenActivityExists() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        when(dossierRepository.compterCreesEntre(any(), any(), anyList())).thenReturn(2L);
        when(audienceRepository.compterTenuesEntre(any(), any(), any(), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterDeposeesEntre(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.compterRendusEntre(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultatsPeriode(any(), any(), anyList())).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(0L);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(any(), any(), anyList())).thenReturn(0d);

        RapportResponse result = service.genererRapport(TypeRapport.MENSUEL, debut, fin, null);

        assertThat(result.estVide()).isFalse();
        assertThat(result.nbNouveauxDossiers()).isEqualTo(2L);
    }

    /**
     * <b>Le périmètre voyage jusqu'aux neuf requêtes</b> (Q-138), et « rien demandé » vaut les deux
     * catégories.
     *
     * <p>C'est ce qui rend « Toutes catégories » exact plutôt que sous-entendu : sans ce test, un
     * indicateur pourrait recevoir une liste vide, ou la liste d'une seule catégorie, et afficher un
     * chiffre trop petit sans que rien ne le dise. Les neuf sont vérifiés par capture, parce qu'un
     * seul oublié suffirait à rompre l'invariant des sommes.
     */
    @Test
    @DisplayName("sans catégorie demandée, les neuf indicateurs portent sur les deux catégories")
    void sansCategorieLePerimetreEstComplet() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList(), anyList()))
                .thenReturn(0L);
        when(etapeProcedureRepository.countDossiersSansEtapeEnCours(anyList(), anyList())).thenReturn(0L);
        when(audienceRepository.compterParStatut(any(), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterNonSoldees(anyList(), anyList())).thenReturn(0L);
        when(alerteRepository.compterParTypeEtStatut(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultats(anyList())).thenReturn(performanceGlobale);
        when(decisionDefinitiveRepository.sommeProvisionsReprises(anyList())).thenReturn(0d);

        service.tableauDeBord(null);

        var attendu = java.util.List.of(CategorieDossier.values());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<CategorieDossier>> perimetre =
                ArgumentCaptor.forClass(java.util.List.class);

        verify(etapeProcedureRepository)
                .countDistinctDossierIdByStatutCycleVieNotIn(anyList(), perimetre.capture());
        assertThat(perimetre.getValue()).as("dossiers actifs").isEqualTo(attendu);

        verify(etapeProcedureRepository).countDossiersSansEtapeEnCours(anyList(), perimetre.capture());
        assertThat(perimetre.getValue()).as("affaires vidées").isEqualTo(attendu);

        verify(audienceRepository).compterParStatut(any(), perimetre.capture());
        assertThat(perimetre.getValue()).as("audiences planifiées").isEqualTo(attendu);

        verify(demandeFraisRepository).compterNonSoldees(anyList(), perimetre.capture());
        assertThat(perimetre.getValue()).as("frais en attente").isEqualTo(attendu);

        verify(alerteRepository).compterParTypeEtStatut(any(), any(), perimetre.capture());
        assertThat(perimetre.getValue()).as("alertes de prorogation").isEqualTo(attendu);

        // Les trois litiges viennent d'une seule requête : la vérifier une fois les couvre tous.
        verify(delibereRepository).calculerResultats(perimetre.capture());
        assertThat(perimetre.getValue()).as("litiges gagnés, perdus et mixtes").isEqualTo(attendu);

        verify(decisionDefinitiveRepository).sommeProvisionsReprises(perimetre.capture());
        assertThat(perimetre.getValue()).as("provisions reprises").isEqualTo(attendu);
    }

    /**
     * Et une catégorie demandée ne laisse passer qu'elle. C'est le pendant du test précédent : sans
     * lui, un périmètre pourrait être reçu puis ignoré, et l'écran afficherait les mêmes chiffres
     * sous les trois choix du sélecteur.
     */
    @Test
    @DisplayName("une catégorie demandée est la seule transmise")
    void uneCategorieDemandeeEstLaSeuleTransmise() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList(), anyList()))
                .thenReturn(0L);
        when(etapeProcedureRepository.countDossiersSansEtapeEnCours(anyList(), anyList())).thenReturn(0L);
        when(audienceRepository.compterParStatut(any(), anyList())).thenReturn(0L);
        when(demandeFraisRepository.compterNonSoldees(anyList(), anyList())).thenReturn(0L);
        when(alerteRepository.compterParTypeEtStatut(any(), any(), anyList())).thenReturn(0L);
        when(delibereRepository.calculerResultats(anyList())).thenReturn(performanceGlobale);
        when(decisionDefinitiveRepository.sommeProvisionsReprises(anyList())).thenReturn(0d);

        service.tableauDeBord(CategorieDossier.EXPLOITATION_LITIGES);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<CategorieDossier>> perimetre =
                ArgumentCaptor.forClass(java.util.List.class);
        verify(delibereRepository).calculerResultats(perimetre.capture());
        assertThat(perimetre.getValue()).containsExactly(CategorieDossier.EXPLOITATION_LITIGES);
    }
}
