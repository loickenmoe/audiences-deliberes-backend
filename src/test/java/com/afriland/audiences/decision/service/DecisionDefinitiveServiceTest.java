package com.afriland.audiences.decision.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.decision.domain.Adjudication;
import com.afriland.audiences.decision.domain.CondamnationPecuniaire;
import com.afriland.audiences.decision.domain.SensCondamnation;
import com.afriland.audiences.decision.domain.StatutPaiementCondamnation;
import com.afriland.audiences.decision.mapper.AdjudicationMapper;
import com.afriland.audiences.decision.mapper.CondamnationMapper;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.decision.web.dto.AdjudicationResponse;
import com.afriland.audiences.decision.web.dto.CondamnationResponse;
import com.afriland.audiences.decision.web.dto.CreerAdjudicationRequest;
import com.afriland.audiences.decision.web.dto.CreerCondamnationRequest;
import com.afriland.audiences.decision.web.dto.ModifierStatutCondamnationRequest;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionDefinitiveServiceTest {

    @Mock private DecisionDefinitiveRepository decisionDefinitiveRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private HistoriqueActionService historiqueActionService;

    private final AdjudicationMapper adjudicationMapper = adjudication -> new AdjudicationResponse(
            adjudication.getId(), adjudication.getDossier() != null ? adjudication.getDossier().getId() : null,
            adjudication.getEtape() != null ? adjudication.getEtape().getId() : null, adjudication.getBeneficiaire(),
            adjudication.getStatutComptabilisation(), adjudication.getRepriseProvision(), adjudication.getRestitutionSoulte(),
            adjudication.getMutation(), adjudication.getReliquat(), adjudication.getDateDecision(),
            adjudication.getCreatedAt(), adjudication.getUpdatedAt());

    private final CondamnationMapper condamnationMapper = condamnation -> new CondamnationResponse(
            condamnation.getId(), condamnation.getDossier() != null ? condamnation.getDossier().getId() : null,
            condamnation.getEtape() != null ? condamnation.getEtape().getId() : null, condamnation.getSens(),
            condamnation.getNaturePaiement(), condamnation.getStatutPaiement(), condamnation.getDateDecision(),
            condamnation.getCreatedAt(), condamnation.getUpdatedAt());

    private DecisionDefinitiveService service;
    private Dossier dossier;
    private EtapeProcedure etapeCloturee;
    private EtapeProcedure etapeEnCours;

    @BeforeEach
    void setUp() {
        service = new DecisionDefinitiveService(decisionDefinitiveRepository, dossierRepository, etapeProcedureRepository,
                historiqueActionService, adjudicationMapper, condamnationMapper);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etapeCloturee = EtapeProcedure.builder().id(1L).dossier(dossier).statutCycleVie(StatutCycleVie.CLOTURE).build();
        etapeEnCours = EtapeProcedure.builder().id(2L).dossier(dossier).statutCycleVie(StatutCycleVie.EN_COURS).build();
    }

    // --- Adjudication (US 8.1) ---

    @Test
    void creerAdjudication_rejectsEtapeNotCloturee() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(2L)).thenReturn(Optional.of(etapeEnCours));

        var requete = new CreerAdjudicationRequest(2L, "Banque", "COMPTABILISE", true, false, false, 0d, LocalDate.now());
        assertThatThrownBy(() -> service.creerAdjudication(1L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void creerAdjudication_nominal() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(1L)).thenReturn(Optional.of(etapeCloturee));
        when(decisionDefinitiveRepository.save(any(Adjudication.class))).thenAnswer(inv -> {
            Adjudication a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        AdjudicationResponse result = service.creerAdjudication(1L,
                new CreerAdjudicationRequest(1L, "Banque Afriland", "COMPTABILISE", true, false, false, 500d, LocalDate.now()));

        assertThat(result.beneficiaire()).isEqualTo("Banque Afriland");
        assertThat(result.reliquat()).isEqualTo(500d);
    }

    // --- Order to pay (US 8.2) ---

    /** An order to pay is tracked on a closed step, like an adjudication (RG-DEF-01). */
    @Test
    void creerCondamnation_surEtapeCloturee() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(1L)).thenReturn(Optional.of(etapeCloturee));
        when(decisionDefinitiveRepository.save(any(CondamnationPecuniaire.class))).thenAnswer(inv -> {
            CondamnationPecuniaire c = inv.getArgument(0);
            c.setId(200L);
            return c;
        });

        CondamnationResponse result = service.creerCondamnation(1L,
                new CreerCondamnationRequest(1L, SensCondamnation.BANQUE_REDEVABLE, "Dommages-intérêts", LocalDate.now()));

        assertThat(result.sens()).isEqualTo(SensCondamnation.BANQUE_REDEVABLE);
        assertThat(result.statutPaiement()).isEqualTo(StatutPaiementCondamnation.EN_ATTENTE);
    }

    /** A freshly opened case whose step is not closed. */
    @Test
    void creerCondamnation_refuseeHorsEtapeCloturee() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(2L)).thenReturn(Optional.of(etapeEnCours));

        var requete = new CreerCondamnationRequest(2L, SensCondamnation.BANQUE_REDEVABLE, "Dommages-intérêts", LocalDate.now());
        assertThatThrownBy(() -> service.creerCondamnation(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CLOTURE");
        verify(decisionDefinitiveRepository, never()).save(any());
    }

    /** Without a step, nothing shows that a judge has ruled: the order to pay is refused. */
    @Test
    void creerCondamnation_refuseeSansEtape() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new CreerCondamnationRequest(null, SensCondamnation.BANQUE_REDEVABLE, "Dommages-intérêts", LocalDate.now());
        assertThatThrownBy(() -> service.creerCondamnation(1L, requete))
                .isInstanceOf(ApiException.class);
        verify(decisionDefinitiveRepository, never()).save(any());
    }

    @Test
    void modifierStatutCondamnation_rejectsUnknownId() {
        when(decisionDefinitiveRepository.findById(200L)).thenReturn(Optional.empty());

        var requete = new ModifierStatutCondamnationRequest(StatutPaiementCondamnation.PAYE);
        assertThatThrownBy(() -> service.modifierStatutCondamnation(200L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void modifierStatutCondamnation_rejectsAdjudicationId() {
        Adjudication adjudication = Adjudication.builder().id(100L).dossier(dossier).build();
        when(decisionDefinitiveRepository.findById(100L)).thenReturn(Optional.of(adjudication));

        var requete = new ModifierStatutCondamnationRequest(StatutPaiementCondamnation.PAYE);
        assertThatThrownBy(() -> service.modifierStatutCondamnation(100L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void modifierStatutCondamnation_nominal() {
        CondamnationPecuniaire condamnation = CondamnationPecuniaire.builder().id(200L).dossier(dossier)
                .sens(SensCondamnation.TIERS_REDEVABLE).naturePaiement("Dommages").statutPaiement(StatutPaiementCondamnation.EN_ATTENTE).build();
        when(decisionDefinitiveRepository.findById(200L)).thenReturn(Optional.of(condamnation));
        when(decisionDefinitiveRepository.save(any(CondamnationPecuniaire.class))).thenAnswer(inv -> inv.getArgument(0));

        CondamnationResponse result = service.modifierStatutCondamnation(200L, new ModifierStatutCondamnationRequest(StatutPaiementCondamnation.PAYE));

        assertThat(result.statutPaiement()).isEqualTo(StatutPaiementCondamnation.PAYE);
    }
}
