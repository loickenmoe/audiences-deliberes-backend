package com.afriland.audiences.delibere.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.Prorogation;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;
import com.afriland.audiences.delibere.mapper.DelibereMapper;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.delibere.repository.ProrogationRepository;
import com.afriland.audiences.delibere.web.dto.CreerProrogationRequest;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import com.afriland.audiences.delibere.web.dto.EnregistrerDelibereRequest;
import com.afriland.audiences.delibere.web.dto.ExpeditionRequest;
import com.afriland.audiences.delibere.web.dto.ProrogationResponse;
import com.afriland.audiences.delibere.web.dto.RabattementRequest;
import com.afriland.audiences.delibere.web.dto.RecoursResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.mapper.EtapeProcedureMapper;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
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
class DelibereServiceTest {

    @Mock private DelibereRepository delibereRepository;
    @Mock private ProrogationRepository prorogationRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private ConfigurationService configurationService;

    private final DelibereMapper delibereMapper = delibere -> new DelibereResponse(
            delibere.getId(),
            delibere.getDossier() != null ? delibere.getDossier().getId() : null,
            delibere.getEtape() != null ? delibere.getEtape().getId() : null,
            delibere.getDateDeliberee(), delibere.getResultat(), delibere.getStatutExpedition(),
            delibere.getDateEcheanceRecours(), delibere.getCreatedAt());

    private final EtapeProcedureMapper etapeProcedureMapper = etape -> new EtapeProcedureResponse(
            etape.getId(), etape.getDossier() != null ? etape.getDossier().getId() : null,
            etape.getType(), etape.getStatutCycleVie());

    private DelibereService service;
    private Dossier dossier;
    private EtapeProcedure etapeEnDelibere;

    @BeforeEach
    void setUp() {
        service = new DelibereService(delibereRepository, prorogationRepository, dossierRepository,
                etapeProcedureRepository, etapeProcedureMapper, utilisateurRepository, historiqueActionService,
                alerteService, configurationService, delibereMapper);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etapeEnDelibere = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_DELIBERE).build();
    }

    // ------------------------------------------------------------------ enregistrerDelibere

    @Test
    void enregistrerDelibere_rejectsWhenEtapeNotEnDelibere() {
        EtapeProcedure etapeEnCours = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_COURS).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), ResultatDelibere.FAVORABLE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("EN_DELIBERE");
    }

    @Test
    void enregistrerDelibere_rejectsMissingEcheanceForDefavorable() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnDelibere));

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), ResultatDelibere.DEFAVORABLE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requise");
    }

    @Test
    void enregistrerDelibere_rejectsEcheanceProvidedForFavorable() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnDelibere));

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), ResultatDelibere.FAVORABLE,
                        LocalDateTime.now().plusDays(30))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("recours");
    }

    @Test
    void enregistrerDelibere_nominalTransitionsEtapeToDelibereVide() {
        LocalDate dateDeliberee = LocalDate.now();
        LocalDateTime echeance = dateDeliberee.plusDays(30).atStartOfDay();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnDelibere));
        when(delibereRepository.save(any(Delibere.class))).thenAnswer(inv -> {
            Delibere d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        DelibereResponse result = service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, dateDeliberee, ResultatDelibere.DEFAVORABLE, echeance));

        assertThat(result.resultat()).isEqualTo(ResultatDelibere.DEFAVORABLE);
        assertThat(result.statutExpedition()).isEqualTo(StatutExpedition.A_LEVER);
        assertThat(etapeEnDelibere.getStatutCycleVie()).isEqualTo(StatutCycleVie.DELIBERE_VIDE);
    }

    // ------------------------------------------------------------------ proroger

    @Test
    void proroger_belowThresholdDoesNotAlert() {
        // RG-DEL-06/Q-44 : un délibéré prorogeable a nécessairement déjà été enregistré, donc son
        // étape est déjà DELIBERE_VIDE (pas de précondition de statut sur la prorogation elle-même).
        EtapeProcedure etapeVide = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.DELIBERE_VIDE).build();
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeVide).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(2L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(configurationService.getOrThrow("SEUIL_PROROGATIONS"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_PROROGATIONS").valeur("10").build());

        ProrogationResponse result = service.proroger(100L, new CreerProrogationRequest(LocalDate.now(), "motif"));

        assertThat(result.compteur()).isEqualTo(3);
        assertThat(result.alerte()).isFalse();
        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void proroger_crossingThresholdAlertsDjAndDja() {
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeEnDelibere).build();
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        Utilisateur dja = Utilisateur.builder().id(21L).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(10L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(configurationService.getOrThrow("SEUIL_PROROGATIONS"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_PROROGATIONS").valeur("10").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        ProrogationResponse result = service.proroger(100L, new CreerProrogationRequest(LocalDate.now(), "motif"));

        assertThat(result.compteur()).isEqualTo(11);
        assertThat(result.alerte()).isTrue();
        verify(alerteService).declencher(eq(TypeAlerte.PROROGATION), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.PROROGATION), eq(1L), eq(dja), any());
    }

    @Test
    void proroger_alreadyAlertedDestinataireIsNotResignaled() {
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeEnDelibere).build();
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(11L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(501L);
            return p;
        });
        when(configurationService.getOrThrow("SEUIL_PROROGATIONS"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_PROROGATIONS").valeur("10").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of());
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.PROROGATION), eq(20L), any())).thenReturn(true);

        ProrogationResponse result = service.proroger(100L, new CreerProrogationRequest(LocalDate.now(), "motif"));

        assertThat(result.alerte()).isTrue();
        verify(alerteService, never()).declencher(eq(TypeAlerte.PROROGATION), any(), eq(dj), any());
    }

    // ------------------------------------------------------------------ rabattre

    @Test
    void rabattre_rejectsWhenEtapeNotDelibereVide() {
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeEnDelibere).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));

        assertThatThrownBy(() -> service.rabattre(100L, new RabattementRequest(LocalDate.now(), "motif")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("DELIBERE_VIDE");
    }

    @Test
    void rabattre_nominalReopensEtapeWithoutOpeningNext() {
        EtapeProcedure etapeVide = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.DELIBERE_VIDE).build();
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeVide).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        EtapeProcedureResponse result = service.rabattre(100L, new RabattementRequest(LocalDate.now(), "renvoi pour complément"));

        assertThat(result.statutCycleVie()).isEqualTo(StatutCycleVie.EN_COURS);
        verify(etapeProcedureRepository, never()).existsByDossierIdAndType(any(), any());
    }

    // ------------------------------------------------------------------ consulterRecours

    @Test
    void consulterRecours_reflectsRecoursExerceWhenNextEtapeExists() {
        EtapeProcedure etapeVide = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_COURS).build();
        LocalDateTime echeance = LocalDateTime.now().minusDays(1);
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeVide)
                .dateEcheanceRecours(echeance).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(etapeProcedureRepository.existsByDossierIdAndType(1L, TypeEtape.RECOURS_1)).thenReturn(true);

        RecoursResponse result = service.consulterRecours(100L);

        assertThat(result.recoursExerce()).isTrue();
        assertThat(result.delaiExpire()).isTrue();
        assertThat(result.cloture()).isFalse();
    }

    @Test
    void consulterRecours_reflectsClotureWhenEtapeAlreadyCloturee() {
        EtapeProcedure etapeCloturee = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.CLOTURE).build();
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeCloturee)
                .dateEcheanceRecours(LocalDateTime.now().minusDays(1)).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));

        RecoursResponse result = service.consulterRecours(100L);

        assertThat(result.cloture()).isTrue();
    }

    // ------------------------------------------------------------------ modifierExpedition

    @Test
    void modifierExpedition_updatesStatut() {
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeEnDelibere)
                .statutExpedition(StatutExpedition.A_LEVER).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(delibereRepository.save(any(Delibere.class))).thenAnswer(inv -> inv.getArgument(0));

        DelibereResponse result = service.modifierExpedition(100L, new ExpeditionRequest(StatutExpedition.LEVEE));

        assertThat(result.statutExpedition()).isEqualTo(StatutExpedition.LEVEE);
    }
}
