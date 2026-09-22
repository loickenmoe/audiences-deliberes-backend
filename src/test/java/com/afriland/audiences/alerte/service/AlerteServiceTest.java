package com.afriland.audiences.alerte.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.AlerteDeclenchee;
import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.StatutDiffusion;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapper;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.alerte.web.dto.StatistiquesDiffusionResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlerteServiceTest {

    @Mock
    private AlerteRepository alerteRepository;

    @Mock
    private AlerteMapper alerteMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Q-109 — sans lui, la mesure de délivrance mentirait :
     * {@code convertAndSendToUser} réussit même quand personne n'écoute.
     */
    @Mock
    private SimpUserRegistry simpUserRegistry;

    /** Q-118 — les notifications externes écoutent l'alerte ; ce service ne les connaît pas. */
    @Mock
    private ApplicationEventPublisher evenements;

    @InjectMocks
    private AlerteService alerteService;

    @Test
    void declencher_persistsWithApplicatifCanalAndPushesToDestinataire() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).keycloakId("kc-5").build();
        when(alerteRepository.save(any(Alerte.class))).thenAnswer(inv -> {
            Alerte a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(alerteMapper.toResponse(any(Alerte.class))).thenReturn(
                new AlerteResponse(1L, 99L, 5L, TypeAlerte.DOUBLON_AFFECTATION, "doublon", CanalAlerte.APPLICATIF,
                        StatutAlerte.DECLENCHEE, null, null));

        Alerte result = alerteService.declencher(TypeAlerte.DOUBLON_AFFECTATION, 99L, destinataire, "doublon");

        ArgumentCaptor<Alerte> captor = ArgumentCaptor.forClass(Alerte.class);
        verify(alerteRepository).save(captor.capture());
        Alerte saved = captor.getValue();
        assertThat(saved.getDossierId()).isEqualTo(99L);
        assertThat(saved.getDestinataire()).isEqualTo(destinataire);
        assertThat(saved.getType()).isEqualTo(TypeAlerte.DOUBLON_AFFECTATION);
        assertThat(saved.getCanal()).isEqualTo(CanalAlerte.APPLICATIF);
        assertThat(saved.getStatut()).isEqualTo(StatutAlerte.DECLENCHEE);
        assertThat(result.getId()).isEqualTo(1L);
        verify(messagingTemplate).convertAndSendToUser(eq("kc-5"), eq("/queue/alertes"), any(AlerteResponse.class));
        // Q-118 : l'événement porte ce qu'il faut aux canaux externes, et pas le texte de l'alerte.
        verify(evenements).publishEvent(new AlerteDeclenchee(1L, TypeAlerte.DOUBLON_AFFECTATION, 99L, destinataire));
    }

    @Test
    void declencher_stillReturnsSavedAlerteWhenWebSocketPushFails() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).keycloakId("kc-5").build();
        when(alerteRepository.save(any(Alerte.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alerteMapper.toResponse(any(Alerte.class))).thenReturn(
                new AlerteResponse(1L, null, 5L, TypeAlerte.CHARGE_MAX_AVOCAT, "charge", CanalAlerte.APPLICATIF,
                        StatutAlerte.DECLENCHEE, null, null));
        doThrow(new MessagingException("broker indisponible"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        Alerte result = alerteService.declencher(TypeAlerte.CHARGE_MAX_AVOCAT, null, destinataire, "charge");

        assertThat(result).isNotNull();
        assertThat(result.getStatut()).isEqualTo(StatutAlerte.DECLENCHEE);
        // Q-109 : la rupture du canal est désormais enregistrée, pas seulement journalisée.
        assertThat(result.getStatutDiffusion()).isEqualTo(StatutDiffusion.ECHEC);
        assertThat(result.getDateDiffusion()).isNotNull();
    }

    /**
     * Q-109 — le cœur de la mesure, et la raison pour laquelle un booléen ne suffisait pas.
     *
     * <p>{@code convertAndSendToUser} ne lève <b>aucune</b> exception quand le destinataire est
     * déconnecté : elle route vers une file sans abonné et retourne normalement. Ces deux tests
     * vérifient que les deux situations sont distinguées, sans quoi le taux afficherait 100 % en
     * permanence — panne comprise.
     */
    @Test
    void declencher_marqueDiffuseeQuandLeDestinataireEstConnecte() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).keycloakId("kc-5").build();
        when(alerteRepository.save(any(Alerte.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alerteMapper.toResponse(any(Alerte.class))).thenReturn(
                new AlerteResponse(1L, null, 5L, TypeAlerte.RAPPEL_AUDIENCE, "J-7", CanalAlerte.APPLICATIF,
                        StatutAlerte.DECLENCHEE, null, null));
        when(simpUserRegistry.getUser("kc-5")).thenReturn(mock(SimpUser.class));

        Alerte result = alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, null, destinataire, "J-7");

        assertThat(result.getStatutDiffusion()).isEqualTo(StatutDiffusion.DIFFUSEE);
        assertThat(result.getDateDiffusion()).isNotNull();
    }

    @Test
    void declencher_marqueDiffereeQuandAucuneSessionNEstOuverte() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).keycloakId("kc-5").build();
        when(alerteRepository.save(any(Alerte.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alerteMapper.toResponse(any(Alerte.class))).thenReturn(
                new AlerteResponse(1L, null, 5L, TypeAlerte.RAPPEL_AUDIENCE, "J-7", CanalAlerte.APPLICATIF,
                        StatutAlerte.DECLENCHEE, null, null));
        when(simpUserRegistry.getUser("kc-5")).thenReturn(null);

        Alerte result = alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, null, destinataire, "J-7");

        // Pas un échec : le repli garanti reste ouvert, seule la remise est plus tardive.
        assertThat(result.getStatutDiffusion()).isEqualTo(StatutDiffusion.DIFFEREE);
        verify(messagingTemplate).convertAndSendToUser(eq("kc-5"), eq("/queue/alertes"), any(AlerteResponse.class));
    }

    /**
     * Q-109 — une alerte remise plus tard reste délivrée au sens de NF-ALR-02 : le canal applicatif
     * comprend la liste des notifications. Seuls les échecs comptent contre nous.
     */
    @Test
    void statistiquesDiffusion_neCompteQueLesEchecsContreLeSysteme() {
        LocalDate debut = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 30);
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.DIFFUSEE), any(), any())).thenReturn(700L);
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.DIFFEREE), any(), any())).thenReturn(295L);
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.ECHEC), any(), any())).thenReturn(5L);
        when(alerteRepository.compterNonTraitees(any(), any())).thenReturn(42L);

        StatistiquesDiffusionResponse stats = alerteService.statistiquesDiffusion(debut, fin);

        assertThat(stats.observees()).isEqualTo(1000L);
        // 995/1000 : les différées sont délivrées, seuls les 5 échecs pèsent.
        assertThat(stats.tauxDelivrance()).isEqualByComparingTo("99.50");
        assertThat(stats.seuilRespecte()).isTrue();
        // Tout autre chose : la proportion de destinataires connectés au moment du déclenchement.
        assertThat(stats.tauxDiffusionTempsReel()).isEqualByComparingTo("70.00");
        assertThat(stats.nonTraitees()).isEqualTo(42L);
    }

    @Test
    void statistiquesDiffusion_signaleLeFranchissementDuSeuilNfAlr02() {
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.DIFFUSEE), any(), any())).thenReturn(80L);
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.DIFFEREE), any(), any())).thenReturn(18L);
        when(alerteRepository.compterParDiffusion(eq(StatutDiffusion.ECHEC), any(), any())).thenReturn(2L);
        when(alerteRepository.compterNonTraitees(any(), any())).thenReturn(0L);

        StatistiquesDiffusionResponse stats = alerteService.statistiquesDiffusion(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(stats.tauxDelivrance()).isEqualByComparingTo("98.00");
        assertThat(stats.seuilRespecte()).isFalse();
    }

    /**
     * Une fenêtre sans aucune alerte observée ne vaut pas un manquement : il n'y a rien à respecter
     * ni à enfreindre. La déclarer non conforme ferait sonner une alarme sur du vide.
     */
    @Test
    void statistiquesDiffusion_uneFenetreVideNEstPasUnManquement() {
        when(alerteRepository.compterParDiffusion(any(), any(), any())).thenReturn(0L);
        when(alerteRepository.compterNonTraitees(any(), any())).thenReturn(0L);

        StatistiquesDiffusionResponse stats = alerteService.statistiquesDiffusion(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(stats.observees()).isZero();
        assertThat(stats.seuilRespecte()).isTrue();
    }

    @Test
    void statistiquesDiffusion_refuseUneFenetreInversee() {
        assertThatThrownBy(() -> alerteService.statistiquesDiffusion(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");
    }

    @Test
    void mesNotifications_filtersByStatutWhenProvided() {
        Utilisateur current = Utilisateur.builder().id(5L).build();
        when(currentUserService.getCurrentUser()).thenReturn(current);
        when(alerteRepository.rechercher(eq(5L), eq(StatutAlerte.DECLENCHEE), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        alerteService.mesNotifications(StatutAlerte.DECLENCHEE, null, null, PageRequest.of(0, 10));

        verify(alerteRepository).rechercher(eq(5L), eq(StatutAlerte.DECLENCHEE), isNull(), isNull(), any());
    }

    @Test
    void mesNotifications_listsAllWhenStatutIsNull() {
        Utilisateur current = Utilisateur.builder().id(5L).build();
        when(currentUserService.getCurrentUser()).thenReturn(current);
        when(alerteRepository.rechercher(eq(5L), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        alerteService.mesNotifications(null, null, null, PageRequest.of(0, 10));

        verify(alerteRepository).rechercher(eq(5L), isNull(), isNull(), isNull(), any());
    }

    /**
     * Q-102 — les deux filtres neufs. Ce que ce test garde n'est pas leur transmission mais le fait
     * que le **destinataire** reste celui du jeton : il n'est pas un paramètre, et aucun filtre ne
     * doit pouvoir le déplacer vers un collègue.
     */
    @Test
    void mesNotifications_transmetTypeEtDossierSansToucherAuDestinataire() {
        Utilisateur current = Utilisateur.builder().id(5L).build();
        when(currentUserService.getCurrentUser()).thenReturn(current);
        when(alerteRepository.rechercher(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        alerteService.mesNotifications(null, TypeAlerte.RAPPEL_AUDIENCE, 42L, PageRequest.of(0, 10));

        verify(alerteRepository).rechercher(eq(5L), isNull(), eq(TypeAlerte.RAPPEL_AUDIENCE), eq(42L), any());
    }

    @Test
    void traiter_marksAsTraiteeWhenCurrentUserIsDestinataire() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).build();
        Alerte alerte = Alerte.builder().id(1L).destinataire(destinataire).statut(StatutAlerte.DECLENCHEE).build();
        when(alerteRepository.findById(1L)).thenReturn(Optional.of(alerte));
        when(currentUserService.getCurrentUser()).thenReturn(destinataire);
        when(alerteRepository.save(any(Alerte.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alerteMapper.toResponse(any(Alerte.class))).thenReturn(
                new AlerteResponse(1L, null, 5L, TypeAlerte.ALARME, "x", CanalAlerte.APPLICATIF,
                        StatutAlerte.TRAITEE, null, null));

        alerteService.traiter(1L);

        assertThat(alerte.getStatut()).isEqualTo(StatutAlerte.TRAITEE);
        assertThat(alerte.getDateTraitement()).isNotNull();
    }

    @Test
    void traiter_rejectsWhenCurrentUserIsNotDestinataire() {
        Utilisateur destinataire = Utilisateur.builder().id(5L).build();
        Utilisateur autre = Utilisateur.builder().id(6L).build();
        Alerte alerte = Alerte.builder().id(1L).destinataire(destinataire).statut(StatutAlerte.DECLENCHEE).build();
        when(alerteRepository.findById(1L)).thenReturn(Optional.of(alerte));
        when(currentUserService.getCurrentUser()).thenReturn(autre);

        assertThatThrownBy(() -> alerteService.traiter(1L)).isInstanceOf(ApiException.class);
        verify(alerteRepository, never()).save(any());
    }

    @Test
    void traiter_throwsNotFoundWhenAlerteMissing() {
        when(alerteRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alerteService.traiter(404L)).isInstanceOf(ApiException.class);
    }

    @Test
    void estDejaSignalee_delegatesToRepository() {
        when(alerteRepository.existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
                TypeAlerte.CHARGE_MAX_AVOCAT, 5L, StatutAlerte.DECLENCHEE, "[avocatId:9] "))
                .thenReturn(true);

        boolean result = alerteService.estDejaSignalee(TypeAlerte.CHARGE_MAX_AVOCAT, 5L, "[avocatId:9] ");

        assertThat(result).isTrue();
    }
}
