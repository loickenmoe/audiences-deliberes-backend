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
        // Management must know how many cases a rate is based on.
        assertThat(result.nbLitigesGagnes()).isEqualTo(3L);
        assertThat(result.nbLitigesPerdus()).isEqualTo(1L);
        assertThat(result.nbLitigesMixtes()).isZero();
        /*
         * "A case is closed when it is at the closed step." A case has several steps, so the rule applies
         * at case level: no step in progress. It is the exact complement of active cases, shown alongside.
         */
        assertThat(result.nbAffairesVidees()).isEqualTo(5L);
        assertThat(result.provisionsReprises()).isEqualTo(500000d);
    }

    /**
     * "Lost" is <b>not</b> "total minus won". {@code ResultatDelibere} has three values: a {@code MIXTE}
     * deliberation is neither a success nor a failure. Deriving it from the total would count partially
     * won cases as losses.
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
        // The three add up to the total: nothing is lost, nothing is assigned by default.
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
        // `SUM` returns `null` on an empty set, not zero: the three counts must survive it.
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
     * <b>The scope reaches all nine queries</b>, and "nothing requested" means both categories. Without
     * this, an indicator could receive an empty or single-category list and silently show too small a
     * figure. All nine are checked by capture, since one omission would break the sum invariant.
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

        // The three litigation counts come from one query: checking it once covers all.
        verify(delibereRepository).calculerResultats(perimetre.capture());
        assertThat(perimetre.getValue()).as("litiges gagnés, perdus et mixtes").isEqualTo(attendu);

        verify(decisionDefinitiveRepository).sommeProvisionsReprises(perimetre.capture());
        assertThat(perimetre.getValue()).as("provisions reprises").isEqualTo(attendu);
    }

    /**
     * A requested category lets only itself through. Otherwise a scope could be received then ignored,
     * and the screen would show the same figures for all three selector choices.
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
