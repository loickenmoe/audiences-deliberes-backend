package com.afriland.audiences.alerte.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapper;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
