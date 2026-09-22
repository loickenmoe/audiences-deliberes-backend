package com.afriland.audiences.audience.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.PeriodeCalendrier;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.mapper.AudienceMapper;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.audience.web.dto.AnnulerAudienceRequest;
import com.afriland.audiences.audience.web.dto.AudienceResponse;
import com.afriland.audiences.audience.web.dto.EnregistrerCompteRenduRequest;
import com.afriland.audiences.audience.web.dto.PlanifierAudienceRequest;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudienceServiceTest {

    @Mock private AudienceRepository audienceRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private DossierAvocatRepository dossierAvocatRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;

    private final AudienceMapper audienceMapper = audience -> new AudienceResponse(
            audience.getId(),
            audience.getDossier() != null ? audience.getDossier().getId() : null,
            audience.getEtape() != null ? audience.getEtape().getId() : null,
            audience.getDatePlanifiee(), audience.getDateTenue(), audience.getCompteRendu(),
            audience.getStatut(), audience.getCreatedAt(), audience.getMotifAnnulation(), audience.getDateAnnulation());

    private AudienceService service;
    private Dossier dossier;
    private EtapeProcedure etapeEnCours;

    @BeforeEach
    void setUp() {
        service = new AudienceService(audienceRepository, dossierRepository, etapeProcedureRepository,
                dossierJuristeRepository, dossierAvocatRepository, utilisateurRepository,
                historiqueActionService, alerteService, audienceMapper);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etapeEnCours = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_COURS).build();
    }

    @Test
    void planifierAudience_rejectsWhenEtapeNotEnCours() {
        EtapeProcedure etapeOuverture = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.OUVERTURE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeOuverture));

        assertThatThrownBy(() -> service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5)), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("EN_COURS");
    }

    @Test
    void planifierAudience_rejectsPastDate() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));

        assertThatThrownBy(() -> service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now()), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("future");
    }

    @Test
    void planifierAudience_rejectsDuplicateWithoutForcer() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.existsByEtapeIdAndDatePlanifieeAndStatutNot(10L, LocalDate.now().plusDays(5), StatutAudience.ANNULEE)).thenReturn(true);

        assertThatThrownBy(() -> service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5)), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("existe déjà");
        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void planifierAudience_confirmedDuplicateAlertsEachActiveDj() {
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.existsByEtapeIdAndDatePlanifieeAndStatutNot(10L, LocalDate.now().plusDays(5), StatutAudience.ANNULEE)).thenReturn(true);
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));

        service.planifierAudience(1L, new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5)), true);

        verify(alerteService).declencher(eq(TypeAlerte.DOUBLON_AUDIENCE), eq(1L), eq(dj), any());
    }

    @Test
    void planifierAudience_nominalCreatesPlanifieeAudience() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.existsByEtapeIdAndDatePlanifieeAndStatutNot(any(), any(), any())).thenReturn(false);
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(200L);
            }
            return a;
        });

        AudienceResponse result = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5)), false);

        assertThat(result.statut()).isEqualTo(StatutAudience.PLANIFIEE);
        assertThat(result.dossierId()).isEqualTo(1L);
        assertThat(result.etapeId()).isEqualTo(10L);
    }

    @Test
    void enregistrerCompteRendu_rejectsWhenAudienceDateInFuture() {
        Audience audience = Audience.builder().id(5L).dossier(dossier).datePlanifiee(LocalDate.now().plusDays(2))
                .statut(StatutAudience.PLANIFIEE).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(audience));

        assertThatThrownBy(() -> service.enregistrerCompteRendu(5L, new EnregistrerCompteRenduRequest("Compte rendu")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("après la tenue");
    }

    @Test
    void enregistrerCompteRendu_nominalMarksTenue() {
        Audience audience = Audience.builder().id(5L).dossier(dossier).datePlanifiee(LocalDate.now().minusDays(1))
                .statut(StatutAudience.PLANIFIEE).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(audience));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> inv.getArgument(0));

        AudienceResponse result = service.enregistrerCompteRendu(5L, new EnregistrerCompteRenduRequest("Compte rendu"));

        assertThat(result.statut()).isEqualTo(StatutAudience.TENUE);
        assertThat(result.compteRendu()).isEqualTo("Compte rendu");
        assertThat(result.dateTenue()).isEqualTo(LocalDate.now());
    }

    /** M15 (Q-68) : onglet « audiences » d'un dossier. */
    @Test
    void listerParDossier_retourneLesAudiencesDuDossier() {
        when(dossierRepository.findById(dossier.getId())).thenReturn(Optional.of(dossier));
        when(audienceRepository.findByDossierIdOrderByDatePlanifieeDesc(eq(dossier.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(
                Audience.builder().id(2L).dossier(dossier).datePlanifiee(LocalDate.now().plusDays(5))
                        .statut(StatutAudience.PLANIFIEE).build(),
                Audience.builder().id(1L).dossier(dossier).datePlanifiee(LocalDate.now().minusDays(5))
                        .statut(StatutAudience.TENUE).build())));

        List<AudienceResponse> result = service.listerParDossier(dossier.getId(), PageRequest.of(0, 10)).content();

        assertThat(result).extracting(AudienceResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void listerParDossier_dossierInconnu_retourne404() {
        when(dossierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listerParDossier(999L, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Dossier introuvable");
        verify(audienceRepository, never()).findByDossierIdOrderByDatePlanifieeDesc(any(), any());
    }

    /** Q-76 : annulation motivée d'une audience planifiée. */
    @Test
    void annulerAudience_nominalPasseAnnuleeAvecMotifEtDate() {
        Audience audience = Audience.builder().id(5L).dossier(dossier).datePlanifiee(LocalDate.now().plusDays(4))
                .statut(StatutAudience.PLANIFIEE).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(audience));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> inv.getArgument(0));

        AudienceResponse result = service.annulerAudience(5L, new AnnulerAudienceRequest("  Renvoi du tribunal  "));

        assertThat(result.statut()).isEqualTo(StatutAudience.ANNULEE);
        assertThat(result.motifAnnulation()).isEqualTo("Renvoi du tribunal");
        assertThat(result.dateAnnulation()).isNotNull();
        verify(historiqueActionService).enregistrer(eq(1L), eq(TypeActionHistorique.AUDIENCE_ANNULEE), any());
    }

    @Test
    void annulerAudience_refuseUneAudienceQuiNEstPlusPlanifiee() {
        Audience tenue = Audience.builder().id(5L).dossier(dossier).datePlanifiee(LocalDate.now().minusDays(1))
                .statut(StatutAudience.TENUE).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(tenue));

        assertThatThrownBy(() -> service.annulerAudience(5L, new AnnulerAudienceRequest("Erreur")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Seule une audience planifiée");
        verify(audienceRepository, never()).save(any());
    }

    @Test
    void enregistrerCompteRendu_refuseUneAudienceAnnulee() {
        Audience annulee = Audience.builder().id(5L).dossier(dossier).datePlanifiee(LocalDate.now().minusDays(1))
                .statut(StatutAudience.ANNULEE).motifAnnulation("Renvoi").dateAnnulation(LocalDateTime.now()).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(annulee));

        assertThatThrownBy(() -> service.enregistrerCompteRendu(5L, new EnregistrerCompteRenduRequest("CR")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("annulée");
        verify(audienceRepository, never()).save(any());
    }

    /** Q-76 : le calendrier et ses exports écartent les audiences annulées (stub strict : autre appel = échec). */
    @Test
    void calendrier_ecarteLesAudiencesAnnulees() {
        LocalDate debut = LocalDate.of(2026, 9, 7);
        when(audienceRepository.findByDatePlanifieeBetweenAndStatutNot(
                debut, PeriodeCalendrier.HEBDOMADAIRE.dateFin(debut), StatutAudience.ANNULEE)).thenReturn(List.of());

        assertThat(service.calendrier(PeriodeCalendrier.HEBDOMADAIRE, debut)).isEmpty();
    }
}
