package com.afriland.audiences.delibere.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.EtatDelibere;
import com.afriland.audiences.delibere.domain.Prorogation;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.delibere.repository.ProrogationRepository;
import com.afriland.audiences.delibere.web.dto.CreerProrogationRequest;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import com.afriland.audiences.delibere.web.dto.EnregistrerDelibereRequest;
import com.afriland.audiences.delibere.web.dto.ExpeditionRequest;
import com.afriland.audiences.delibere.web.dto.ProrogationResponse;
import com.afriland.audiences.delibere.web.dto.RabattementRequest;
import com.afriland.audiences.delibere.web.dto.RecoursResponse;
import com.afriland.audiences.delibere.web.dto.ViderDelibereRequest;
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

/**
 * Cycle d'un délibéré (Q-78) : mise en délibéré → prorogations / rabattement pendant l'attente →
 * vidage par le résultat. L'enregistrement d'un délibéré déjà rendu, en un seul geste, reste possible.
 */
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
                alerteService, configurationService);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etapeEnDelibere = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_DELIBERE).build();
    }

    /** Délibéré mis en délibéré, date annoncée dans une semaine, sans résultat. */
    private Delibere enAttente() {
        return Delibere.builder().id(100L).dossier(dossier).etape(etapeEnDelibere)
                .dateDeliberee(LocalDate.now().plusDays(7)).build();
    }

    /** Délibéré déjà vidé : étape « Délibéré vidé », résultat connu, expédition ouverte. */
    private Delibere vide() {
        EtapeProcedure etapeVide = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.DELIBERE_VIDE).build();
        return Delibere.builder().id(100L).dossier(dossier).etape(etapeVide).dateDeliberee(LocalDate.now())
                .resultat(ResultatDelibere.FAVORABLE).statutExpedition(StatutExpedition.A_LEVER).build();
    }

    private void seuilDeProrogations(String valeur) {
        when(configurationService.getOrThrow("SEUIL_PROROGATIONS"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_PROROGATIONS").valeur(valeur).build());
    }

    private void enregistrementRenvoieLeDelibere() {
        when(delibereRepository.save(any(Delibere.class))).thenAnswer(inv -> {
            Delibere d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(100L);
            }
            return d;
        });
    }

    private void etapeDuDossier() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnDelibere));
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
    void enregistrerDelibere_sansResultat_metEnDelibereSansToucherLEtape() {
        etapeDuDossier();
        enregistrementRenvoieLeDelibere();

        DelibereResponse result = service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now().plusDays(15), null, null));

        assertThat(result.etat()).isEqualTo(EtatDelibere.EN_ATTENTE);
        assertThat(result.resultat()).isNull();
        assertThat(result.statutExpedition()).as("pas d'expédition avant la décision").isNull();
        assertThat(etapeEnDelibere.getStatutCycleVie()).isEqualTo(StatutCycleVie.EN_DELIBERE);
        verify(etapeProcedureRepository, never()).save(any());
        verify(historiqueActionService).enregistrer(eq(1L), eq(TypeActionHistorique.DELIBERE_MISE_EN_DELIBERE), any());
    }

    @Test
    void enregistrerDelibere_refuseUneEcheanceSansResultat() {
        etapeDuDossier();

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), null, LocalDateTime.now().plusDays(30))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("se saisit avec le résultat");
        verify(delibereRepository, never()).save(any());
    }

    @Test
    void enregistrerDelibere_refuseUnSecondDelibereEnAttenteSurLaMemeEtape() {
        etapeDuDossier();
        when(delibereRepository.existsByEtapeIdAndResultatIsNullAndDateRabattementIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now().plusDays(15), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà en attente");
        verify(delibereRepository, never()).save(any());
    }

    @Test
    void enregistrerDelibere_rejectsMissingEcheanceForDefavorable() {
        etapeDuDossier();

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), ResultatDelibere.DEFAVORABLE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requise");
        verify(delibereRepository, never()).save(any());
    }

    @Test
    void enregistrerDelibere_rejectsEcheanceProvidedForFavorable() {
        etapeDuDossier();

        assertThatThrownBy(() -> service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, LocalDate.now(), ResultatDelibere.FAVORABLE,
                        LocalDateTime.now().plusDays(30))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("recours");
    }

    @Test
    void enregistrerDelibere_avecResultat_enregistreEtVideDUnMemeGeste() {
        LocalDate dateDeliberee = LocalDate.now();
        LocalDateTime echeance = dateDeliberee.plusDays(30).atStartOfDay();
        etapeDuDossier();
        enregistrementRenvoieLeDelibere();
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        DelibereResponse result = service.enregistrerDelibere(1L,
                new EnregistrerDelibereRequest(10L, dateDeliberee, ResultatDelibere.DEFAVORABLE, echeance));

        assertThat(result.etat()).isEqualTo(EtatDelibere.VIDE);
        assertThat(result.resultat()).isEqualTo(ResultatDelibere.DEFAVORABLE);
        assertThat(result.statutExpedition()).isEqualTo(StatutExpedition.A_LEVER);
        assertThat(etapeEnDelibere.getStatutCycleVie()).isEqualTo(StatutCycleVie.DELIBERE_VIDE);
    }

    // ------------------------------------------------------------------ viderDelibere

    @Test
    void viderDelibere_poseLeResultatEtFaitPasserLEtapeDelibereVide() {
        Delibere delibere = enAttente();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        DelibereResponse result = service.viderDelibere(100L, new ViderDelibereRequest(ResultatDelibere.FAVORABLE, null));

        assertThat(result.etat()).isEqualTo(EtatDelibere.VIDE);
        assertThat(result.statutExpedition()).isEqualTo(StatutExpedition.A_LEVER);
        assertThat(etapeEnDelibere.getStatutCycleVie()).isEqualTo(StatutCycleVie.DELIBERE_VIDE);
    }

    @Test
    void viderDelibere_exigeLEcheancePourUnResultatDefavorable() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));

        assertThatThrownBy(() -> service.viderDelibere(100L, new ViderDelibereRequest(ResultatDelibere.MIXTE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requise");
        verify(etapeProcedureRepository, never()).save(any());
    }

    @Test
    void viderDelibere_refuseUnDelibereDejaVide() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(vide()));

        assertThatThrownBy(() -> service.viderDelibere(100L, new ViderDelibereRequest(ResultatDelibere.FAVORABLE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà vidé");
    }

    // ------------------------------------------------------------------ proroger

    @Test
    void proroger_reporteLaDateAnnonceeSansAlerteSousLeSeuil() {
        Delibere delibere = enAttente();
        LocalDate nouvelleDate = LocalDate.now().plusDays(30);
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(2L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        seuilDeProrogations("10");

        ProrogationResponse result = service.proroger(100L, new CreerProrogationRequest(nouvelleDate, "Juge empêché"));

        assertThat(result.compteur()).isEqualTo(3);
        assertThat(result.alerte()).isFalse();
        assertThat(delibere.getDateDeliberee()).as("« DÉLIBÉRÉ PROROGÉ AU [date] »").isEqualTo(nouvelleDate);
        assertThat(etapeEnDelibere.getStatutCycleVie()).isEqualTo(StatutCycleVie.EN_DELIBERE);
        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void proroger_crossingThresholdAlertsDjAndDja() {
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        Utilisateur dja = Utilisateur.builder().id(21L).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(10L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        seuilDeProrogations("10");
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        ProrogationResponse result = service.proroger(100L,
                new CreerProrogationRequest(LocalDate.now().plusDays(30), "motif"));

        assertThat(result.compteur()).isEqualTo(11);
        assertThat(result.alerte()).isTrue();
        verify(alerteService).declencher(eq(TypeAlerte.PROROGATION), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.PROROGATION), eq(1L), eq(dja), any());
    }

    @Test
    void proroger_alreadyAlertedDestinataireIsNotResignaled() {
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));
        when(prorogationRepository.countByDelibereId(100L)).thenReturn(11L);
        when(prorogationRepository.save(any(Prorogation.class))).thenAnswer(inv -> {
            Prorogation p = inv.getArgument(0);
            p.setId(501L);
            return p;
        });
        seuilDeProrogations("10");
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of());
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.PROROGATION), eq(20L), any())).thenReturn(true);

        ProrogationResponse result = service.proroger(100L,
                new CreerProrogationRequest(LocalDate.now().plusDays(30), "motif"));

        assertThat(result.alerte()).isTrue();
        verify(alerteService, never()).declencher(eq(TypeAlerte.PROROGATION), any(), eq(dj), any());
    }

    @Test
    void proroger_refuseUnDelibereDejaVide() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(vide()));

        assertThatThrownBy(() -> service.proroger(100L, new CreerProrogationRequest(LocalDate.now().plusDays(30), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà vidé");
        verify(prorogationRepository, never()).save(any());
    }

    @Test
    void proroger_refuseUneDateQuiNEstPasFuture() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));

        assertThatThrownBy(() -> service.proroger(100L, new CreerProrogationRequest(LocalDate.now(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("future");
        verify(prorogationRepository, never()).save(any());
    }

    @Test
    void listerProrogations_signaleCellesQuiDepassentLeSeuil() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));
        seuilDeProrogations("1");
        when(prorogationRepository.findByDelibereIdOrderByCompteurAsc(100L)).thenReturn(List.of(
                Prorogation.builder().id(1L).date(LocalDate.now().plusDays(10)).compteur(1).build(),
                Prorogation.builder().id(2L).date(LocalDate.now().plusDays(20)).compteur(2).build()));

        assertThat(service.listerProrogations(100L)).extracting(ProrogationResponse::alerte).containsExactly(false, true);
    }

    // ------------------------------------------------------------------ rabattre

    @Test
    void rabattre_refuseUnDelibereDejaVide() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(vide()));

        assertThatThrownBy(() -> service.rabattre(100L, new RabattementRequest(LocalDate.now(), "motif")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà vidé");
        verify(etapeProcedureRepository, never()).save(any());
    }

    @Test
    void rabattre_marqueLeDelibereEtRouvreLEtapeSansOuvrirLaSuivante() {
        Delibere delibere = enAttente();
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(delibere));
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        EtapeProcedureResponse result = service.rabattre(100L,
                new RabattementRequest(LocalDate.now(), "  Réouverture des débats  "));

        assertThat(result.statutCycleVie()).isEqualTo(StatutCycleVie.EN_COURS);
        assertThat(delibere.getDateRabattement()).isEqualTo(LocalDate.now());
        assertThat(delibere.getMotifRabattement()).isEqualTo("Réouverture des débats");
        verify(etapeProcedureRepository, never()).existsByDossierIdAndType(any(), any());
    }

    // ------------------------------------------------------------------ consulterRecours

    @Test
    void consulterRecours_reflectsRecoursExerceWhenNextEtapeExists() {
        EtapeProcedure etapeEnCours = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.EN_COURS).build();
        Delibere delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeEnCours)
                .dateEcheanceRecours(LocalDateTime.now().minusDays(1)).build();
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
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(vide()));
        when(delibereRepository.save(any(Delibere.class))).thenAnswer(inv -> inv.getArgument(0));

        DelibereResponse result = service.modifierExpedition(100L, new ExpeditionRequest(StatutExpedition.LEVEE));

        assertThat(result.statutExpedition()).isEqualTo(StatutExpedition.LEVEE);
    }

    @Test
    void modifierExpedition_refuseAvantQueLeDelibereSoitVide() {
        when(delibereRepository.findById(100L)).thenReturn(Optional.of(enAttente()));

        assertThatThrownBy(() -> service.modifierExpedition(100L, new ExpeditionRequest(StatutExpedition.LEVEE)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vidé");
        verify(delibereRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ listerParDossier (M15, Q-68)

    @Test
    void listerParDossier_retourneLesDeliberesDuDossierAvecLeurEtat() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(delibereRepository.findByDossierIdOrderByDateDelibereeDesc(1L)).thenReturn(List.of(
                enAttente(),
                Delibere.builder().id(1L).dossier(dossier).etape(etapeEnDelibere)
                        .dateDeliberee(LocalDate.now().minusMonths(1))
                        .dateRabattement(LocalDate.now().minusDays(20)).motifRabattement("Réouverture").build()));
        // Appelé pour chaque délibéré de la liste : réponse selon l'identifiant (le mode strict de
        // Mockito refuserait un stub posé sur 100L puis appelé avec 1L).
        when(prorogationRepository.countByDelibereId(any())).thenAnswer(inv -> Long.valueOf(100L).equals(inv.getArgument(0)) ? 2L : 0L);

        List<DelibereResponse> result = service.listerParDossier(1L);

        assertThat(result).extracting(DelibereResponse::etat).containsExactly(EtatDelibere.EN_ATTENTE, EtatDelibere.RABATTU);
        assertThat(result.get(0).nombreProrogations()).isEqualTo(2L);
    }

    @Test
    void listerParDossier_dossierInconnu_retourne404() {
        when(dossierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listerParDossier(999L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Dossier introuvable");
        verify(delibereRepository, never()).findByDossierIdOrderByDateDelibereeDesc(any());
    }
}
