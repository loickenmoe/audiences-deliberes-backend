package com.afriland.audiences.notification.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Redirection;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Transport;
import com.afriland.audiences.notification.config.NotificationEngineProperties.TypeTransport;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse.LigneCanal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisionNotificationServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    @Mock private NotificationEnvoiRepository repository;

    private SupervisionNotificationService service;

    @BeforeEach
    void preparer() {
        NotificationEngineProperties proprietes = new NotificationEngineProperties(false, "audience-deliberer",
                "0123456789abcdef0123456789abcdef", 5, Duration.ofHours(2), new Redirection(false, null, null, null),
                new Transport(TypeTransport.JOURNAL, "t", "p", "u", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5));
        service = new SupervisionNotificationService(repository, proprietes);
        lenient().when(repository.trouverEnDefaut(any(), any(), any())).thenReturn(List.of());
    }

    private static NotificationEnvoi envoi(CanalEngine canal, String destinataire) {
        return NotificationEnvoi.aRemettre(
                new Origine("alerte-42", "RAPPEL_AUDIENCE", 5L, 42L, TypeDestinataire.AVOCAT, 9L),
                canal, destinataire, "Objet", "Texte");
    }

    private LigneCanal ligne(SupervisionNotificationsResponse reponse, CanalEngine canal) {
        return reponse.canaux().stream().filter(l -> l.canal().equals(canal.name())).findFirst().orElseThrow();
    }

    @Test
    void chaque_canal_a_sa_ligne_meme_sans_aucun_envoi() {
        when(repository.compterParCanalEtStatut(any(), any())).thenReturn(List.of());

        SupervisionNotificationsResponse reponse = service.superviser(DEBUT, FIN);

        assertThat(reponse.canaux()).extracting(LigneCanal::canal)
                .containsExactly("EMAIL", "SMS", "WHATSAPP", "TEAMS");
        assertThat(ligne(reponse, CanalEngine.SMS).total()).isZero();
        assertThat(ligne(reponse, CanalEngine.SMS).tauxAboutissement()).isEqualTo(BigDecimal.ZERO);
        assertThat(reponse.engineActif()).isFalse();
    }

    @Test
    void les_comptes_sont_repartis_par_canal_et_par_statut() {
        when(repository.compterParCanalEtStatut(any(), any())).thenReturn(List.of(
                new Object[] {CanalEngine.EMAIL, StatutEnvoi.ENVOYEE, 8L},
                new Object[] {CanalEngine.EMAIL, StatutEnvoi.ECHEC, 2L},
                new Object[] {CanalEngine.SMS, StatutEnvoi.PUBLIEE, 3L},
                new Object[] {CanalEngine.SMS, StatutEnvoi.SANS_RETOUR, 1L},
                new Object[] {CanalEngine.SMS, StatutEnvoi.NON_JOIGNABLE, 4L}));

        SupervisionNotificationsResponse reponse = service.superviser(DEBUT, FIN);

        LigneCanal courriel = ligne(reponse, CanalEngine.EMAIL);
        assertThat(courriel.confirmees()).isEqualTo(8);
        assertThat(courriel.echecs()).isEqualTo(2);
        assertThat(courriel.total()).isEqualTo(10);
        assertThat(courriel.tauxAboutissement()).isEqualByComparingTo("80.0");

        LigneCanal sms = ligne(reponse, CanalEngine.SMS);
        assertThat(sms.remises()).isEqualTo(3);
        assertThat(sms.sansRetour()).isEqualTo(1);
        assertThat(sms.nonJoignables()).isEqualTo(4);
        // Les lignes sans coordonnée sortent du calcul : elles disent une fiche incomplète, pas un canal en panne.
        assertThat(sms.tauxAboutissement()).isEqualByComparingTo("75.0");
    }

    @Test
    void une_fenetre_inversee_est_refusee() {
        assertThatThrownBy(() -> service.superviser(FIN, DEBUT))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");
    }

    // ── Relance manuelle (D17) ──

    @Test
    void relancer_remet_en_file_et_repart_de_zero() {
        NotificationEnvoi echoue = envoi(CanalEngine.EMAIL, "prestataire.test@example.cm");
        echoue.echouerTentative("courtier injoignable", null);
        when(repository.findById(1L)).thenReturn(Optional.of(echoue));

        service.relancer(1L);

        assertThat(echoue.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(echoue.getTentatives()).isZero();
        assertThat(echoue.getDerniereErreur()).isNull();
        assertThat(echoue.getProchaineTentative()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void relancer_un_envoi_sans_retour_est_permis() {
        NotificationEnvoi sansRetour = envoi(CanalEngine.SMS, "+237600000000");
        sansRetour.marquerPubliee("DAPR", "+237600000000");
        sansRetour.marquerSansRetour();
        when(repository.findById(2L)).thenReturn(Optional.of(sansRetour));

        service.relancer(2L);

        assertThat(sansRetour.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
    }

    @Test
    void relancer_ce_qui_n_a_pas_echoue_est_refuse() {
        NotificationEnvoi enAttente = envoi(CanalEngine.EMAIL, "a@b.cm");
        when(repository.findById(3L)).thenReturn(Optional.of(enAttente));

        assertThatThrownBy(() -> service.relancer(3L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pas à relancer");
    }

    /** Sans coordonnée, relancer ne ferait que refaire le même constat : la fiche doit être corrigée. */
    @Test
    void relancer_un_destinataire_non_joignable_est_refuse() {
        NotificationEnvoi nonJoignable = NotificationEnvoi.nonJoignable(
                new Origine("alerte-42", "RAPPEL_AUDIENCE", 5L, 42L, TypeDestinataire.AVOCAT, 9L),
                CanalEngine.SMS, "COORDONNEE_ABSENTE");
        when(repository.findById(4L)).thenReturn(Optional.of(nonJoignable));

        assertThatThrownBy(() -> service.relancer(4L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("corriger la fiche");
    }

    @Test
    void relancer_une_notification_inconnue_donne_un_404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relancer(9L)).isInstanceOf(ApiException.class);
    }
}
