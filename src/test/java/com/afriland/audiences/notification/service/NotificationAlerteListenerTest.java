package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.AlerteDeclenchee;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.service.Route.MotifNonJoignable;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAlerteListenerTest {

    @Mock private RoutageNotification routage;
    @Mock private ReferencesDossier referencesDossier;
    @Mock private NotificationEnvoiRepository repository;

    private NotificationAlerteListener listener;

    private final Utilisateur juriste = Utilisateur.builder().id(7L).email("juriste.test@afrilandfirstbank.com").build();

    @BeforeEach
    void preparer() {
        listener = new NotificationAlerteListener(routage, new RedacteurNotification(),
                new LiensApplication("http://localhost:3000"), referencesDossier, repository);
    }

    @SuppressWarnings("unchecked")
    private List<NotificationEnvoi> enregistres() {
        ArgumentCaptor<List<NotificationEnvoi>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void une_alerte_routee_vers_Outlook_et_Teams_donne_une_ligne_par_canal() {
        when(routage.pourUtilisateur(TypeAlerte.RAPPEL_AUDIENCE, juriste)).thenReturn(List.of(
                new Route("OUTLOOK", CanalEngine.EMAIL, "juriste.test@afrilandfirstbank.com", null),
                new Route("TEAMS", CanalEngine.TEAMS, "juriste.test@afrilandfirstbank.com", null)));
        when(referencesDossier.reference(5L)).thenReturn(Optional.of("DOS-2026-014"));

        listener.surAlerteDeclenchee(new AlerteDeclenchee(42L, TypeAlerte.RAPPEL_AUDIENCE, 5L, juriste));

        List<NotificationEnvoi> envois = enregistres();
        assertThat(envois).extracting(NotificationEnvoi::getEventId).containsExactly("alerte-42-EMAIL", "alerte-42-TEAMS");
        NotificationEnvoi outlook = envois.get(0);
        assertThat(outlook.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(outlook.getTypeEvenement()).isEqualTo("RAPPEL_AUDIENCE");
        assertThat(outlook.getAlerteId()).isEqualTo(42L);
        assertThat(outlook.getDossierId()).isEqualTo(5L);
        assertThat(outlook.getDestinataireType()).isEqualTo(TypeDestinataire.UTILISATEUR);
        assertThat(outlook.getDestinataireRef()).isEqualTo(7L);
        assertThat(outlook.getSujet()).isEqualTo("[Audiences et délibérés] Rappel d'audience - DOS-2026-014");
        assertThat(outlook.getCorps()).contains("http://localhost:3000/dossiers/5").startsWith("<p>");
        assertThat(envois.get(1).getCorps()).doesNotContain("<p>");
    }

    @Test
    void un_canal_sans_adresse_devient_une_ligne_non_joignable_avec_son_motif() {
        when(routage.pourUtilisateur(TypeAlerte.ALARME, juriste)).thenReturn(List.of(
                new Route("OUTLOOK", CanalEngine.EMAIL, null, MotifNonJoignable.COORDONNEE_INVALIDE)));

        listener.surAlerteDeclenchee(new AlerteDeclenchee(43L, TypeAlerte.ALARME, null, juriste));

        NotificationEnvoi envoi = enregistres().get(0);
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.NON_JOIGNABLE);
        assertThat(envoi.getDerniereErreur()).isEqualTo("COORDONNEE_INVALIDE");
        assertThat(envoi.getCorps()).isNull();
    }

    @Test
    void aucun_canal_externe_rien_n_est_ecrit() {
        when(routage.pourUtilisateur(TypeAlerte.PAIEMENT_EFFECTUE, juriste)).thenReturn(List.of());

        listener.surAlerteDeclenchee(new AlerteDeclenchee(44L, TypeAlerte.PAIEMENT_EFFECTUE, 5L, juriste));

        verify(repository, never()).saveAll(any());
    }

    /** A notification must never make the business action that triggers it fail. */
    @Test
    void un_echec_de_preparation_est_journalise_sans_remonter() {
        when(routage.pourUtilisateur(TypeAlerte.DOCUMENT_CHARGE, juriste)).thenThrow(new IllegalStateException("réglage illisible"));

        assertThatCode(() -> listener.surAlerteDeclenchee(new AlerteDeclenchee(45L, TypeAlerte.DOCUMENT_CHARGE, 5L, juriste)))
                .doesNotThrowAnyException();
        verify(repository, never()).saveAll(any());
    }
}
