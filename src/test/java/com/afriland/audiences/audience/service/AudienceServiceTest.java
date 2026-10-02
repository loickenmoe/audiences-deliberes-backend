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
import org.junit.jupiter.api.DisplayName;
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
            audience.getDatePlanifiee(), audience.getDateTenue(), audience.getJuridiction(),
            audience.getCompteRendu(), audience.getStatut(), audience.getCreatedAt(),
            audience.getMotifAnnulation(), audience.getDateAnnulation());

    private AudienceService service;
    private Dossier dossier;
    private EtapeProcedure etapeEnCours;

    @BeforeEach
    void setUp() {
        service = new AudienceService(audienceRepository, dossierRepository, etapeProcedureRepository,
                dossierJuristeRepository, dossierAvocatRepository, utilisateurRepository,
                historiqueActionService, alerteService, audienceMapper);

        // Ouvert il y a un an : les audiences régularisées s'y rattachent, celles d'avant non.
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001")
                .createdAt(LocalDateTime.now().minusYears(1)).build();
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
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5), null), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("EN_COURS");
    }

/**
     * RG-AUD-07 amendée (2026-09-23) : l'audience du jour s'enregistre, et son compte rendu est
     * recevable dans la foulée — c'était le besoin à l'origine de l'arbitrage.
     */
    @Test
    void une_audience_peut_etre_enregistree_le_jour_meme() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AudienceResponse resultat = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now(), null), false);

        assertThat(resultat.datePlanifiee()).isEqualTo(LocalDate.now());
        assertThat(resultat.statut()).isEqualTo(StatutAudience.PLANIFIEE);
    }

    /**
     * V34 : l'audience porte la juridiction où elle se tient. Sans indication, elle reprend celle du
     * dossier — exacte au premier degré, qui est le cas courant.
     */
    @Test
    @DisplayName("sans indication, l'audience se tient là où le dossier a été saisi")
    void une_audience_herite_de_la_juridiction_du_dossier() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(20L);
            return a;
        });

        AudienceResponse resultat = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(3), null), false);

        assertThat(resultat.juridiction()).isEqualTo(dossier.getJuridictionSaisie());
    }

    /**
     * Le cas qui motive le champ : une audience d'appel ne se tient pas devant le tribunal qui a
     * jugé. Afficher la juridiction du dossier ne laissait pas l'information manquante — elle la
     * rendait fausse.
     */
    @Test
    @DisplayName("une audience d'appel se tient devant sa propre juridiction")
    void une_audience_peut_porter_sa_propre_juridiction() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(21L);
            return a;
        });

        AudienceResponse resultat = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(3), "  Cour d'appel du Littoral  "),
                false);

        // Les espaces de saisie sont retirés : « Cour d'appel » et « Cour d'appel  » sont le même lieu.
        assertThat(resultat.juridiction()).isEqualTo("Cour d'appel du Littoral");
    }

    /** Une saisie vide n'est pas une juridiction : elle vaut absence, donc celle du dossier. */
    @Test
    void une_juridiction_vide_vaut_celle_du_dossier() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(22L);
            return a;
        });

        AudienceResponse resultat = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(3), "   "), false);

        assertThat(resultat.juridiction()).isEqualTo(dossier.getJuridictionSaisie());
    }

    /** Une audience tenue sans avoir été saisie se régularise : sinon, elle était perdue à jamais. */
    @Test
    void une_audience_deja_tenue_peut_etre_regularisee() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> {
            Audience a = inv.getArgument(0);
            a.setId(8L);
            return a;
        });

        AudienceResponse resultat = service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().minusDays(10), null), false);

        assertThat(resultat.datePlanifiee()).isEqualTo(LocalDate.now().minusDays(10));
    }

    /** La seule borne : une audience ne peut pas précéder le dossier qui la porte (faute de frappe). */
    @Test
    void une_audience_anterieure_a_l_ouverture_du_dossier_est_refusee() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));

        assertThatThrownBy(() -> service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, dossier.getCreatedAt().toLocalDate().minusDays(1), null), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ouverture du dossier");
    }

    @Test
    void planifierAudience_rejectsDuplicateWithoutForcer() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etapeEnCours));
        when(audienceRepository.existsByEtapeIdAndDatePlanifieeAndStatutNot(10L, LocalDate.now().plusDays(5), StatutAudience.ANNULEE)).thenReturn(true);

        assertThatThrownBy(() -> service.planifierAudience(1L,
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5), null), false))
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

        service.planifierAudience(1L, new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5), null), true);

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
                new PlanifierAudienceRequest(10L, LocalDate.now().plusDays(5), null), false);

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
        assertThat(result.dateTenue()).isEqualTo(audience.getDatePlanifiee());
    }

    /**
     * QF-32 : une audience appartient au mois où elle s'est tenue, pas à celui où le juriste a
     * trouvé le temps d'en écrire le compte rendu. C'est cette date que compte le rapport d'activité.
     */
    @Test
    void un_compte_rendu_saisi_en_retard_ne_deplace_pas_l_audience() {
        LocalDate ilYaDeuxMois = LocalDate.now().minusMonths(2);
        Audience audience = Audience.builder().id(5L).dossier(dossier).datePlanifiee(ilYaDeuxMois)
                .statut(StatutAudience.PLANIFIEE).build();
        when(audienceRepository.findById(5L)).thenReturn(Optional.of(audience));
        when(audienceRepository.save(any(Audience.class))).thenAnswer(inv -> inv.getArgument(0));

        AudienceResponse result = service.enregistrerCompteRendu(5L, new EnregistrerCompteRenduRequest("Tardif"));

        assertThat(result.dateTenue()).isEqualTo(ilYaDeuxMois);
        assertThat(result.dateTenue()).isNotEqualTo(LocalDate.now());
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
