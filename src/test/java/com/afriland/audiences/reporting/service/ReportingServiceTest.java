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
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList())).thenReturn(12L);
        when(audienceRepository.countByStatut(StatutAudience.PLANIFIEE)).thenReturn(7L);
        when(demandeFraisRepository.countByStatutCircuitNotIn(anyList())).thenReturn(3L);
        when(alerteRepository.countByTypeAndStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE)).thenReturn(2L);
        when(delibereRepository.calculerTauxSuccesRecouvrement()).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(3L);
        when(performanceGlobale.getTotal()).thenReturn(4L);
        when(decisionDefinitiveRepository.sommeProvisionsReprises()).thenReturn(500000d);

        TableauDeBordResponse result = service.tableauDeBord();

        assertThat(result.nbDossiersActifs()).isEqualTo(12L);
        assertThat(result.nbAudiencesPlanifiees()).isEqualTo(7L);
        assertThat(result.nbDemandesFraisEnAttente()).isEqualTo(3L);
        assertThat(result.nbProrogationsAlerte()).isEqualTo(2L);
        assertThat(result.tauxSuccesRecouvrement()).isEqualTo(0.75d);
        assertThat(result.provisionsReprises()).isEqualTo(500000d);
    }

    @Test
    void tableauDeBord_defaultsTauxToZeroWhenNoDelibere() {
        when(etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(anyList())).thenReturn(0L);
        when(audienceRepository.countByStatut(any())).thenReturn(0L);
        when(demandeFraisRepository.countByStatutCircuitNotIn(anyList())).thenReturn(0L);
        when(alerteRepository.countByTypeAndStatut(any(), any())).thenReturn(0L);
        when(delibereRepository.calculerTauxSuccesRecouvrement()).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(null);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprises()).thenReturn(null);

        TableauDeBordResponse result = service.tableauDeBord();

        assertThat(result.tauxSuccesRecouvrement()).isZero();
        assertThat(result.provisionsReprises()).isZero();
    }

    @Test
    void genererRapport_scopesToPeriodAndDetectsEmpty() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        when(dossierRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(audienceRepository.countByStatutAndDateTenueBetween(eq(StatutAudience.TENUE), eq(debut), eq(fin))).thenReturn(0L);
        when(demandeFraisRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(delibereRepository.countByDateDelibereeBetweenAndResultatIsNotNull(debut, fin)).thenReturn(0L);
        when(delibereRepository.calculerTauxSuccesRecouvrementPeriode(debut, fin)).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(0L);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(debut, fin)).thenReturn(0d);

        RapportResponse result = service.genererRapport(TypeRapport.MENSUEL, debut, fin);

        assertThat(result.estVide()).isTrue();
        assertThat(result.type()).isEqualTo(TypeRapport.MENSUEL);
    }

    @Test
    void genererRapport_nonEmptyWhenActivityExists() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        when(dossierRepository.countByCreatedAtBetween(any(), any())).thenReturn(2L);
        when(audienceRepository.countByStatutAndDateTenueBetween(any(), any(), any())).thenReturn(0L);
        when(demandeFraisRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(delibereRepository.countByDateDelibereeBetweenAndResultatIsNotNull(any(), any())).thenReturn(0L);
        when(delibereRepository.calculerTauxSuccesRecouvrementPeriode(any(), any())).thenReturn(performanceGlobale);
        when(performanceGlobale.getFavorables()).thenReturn(0L);
        when(performanceGlobale.getTotal()).thenReturn(0L);
        when(decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(any(), any())).thenReturn(0d);

        RapportResponse result = service.genererRapport(TypeRapport.MENSUEL, debut, fin);

        assertThat(result.estVide()).isFalse();
        assertThat(result.nbNouveauxDossiers()).isEqualTo(2L);
    }
}
