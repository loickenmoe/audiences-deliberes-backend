package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Redirection;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Transport;
import com.afriland.audiences.notification.config.NotificationEngineProperties.TypeTransport;
import com.afriland.audiences.notification.domain.NotificationAccuse;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationAccuseRepository;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccuseNotificationServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final OffsetDateTime INSTANT = OffsetDateTime.of(2026, 9, 23, 10, 30, 0, 0, ZoneOffset.UTC);

    @Mock private NotificationAccuseRepository accuseRepository;
    @Mock private NotificationEnvoiRepository envoiRepository;

    private AccuseNotificationService service;

    private static NotificationEngineProperties proprietes() {
        return new NotificationEngineProperties(true, "audience-deliberer", SECRET, 5, Duration.ofHours(2),
                new Redirection(false, null, null, null),
                new Transport(TypeTransport.DAPR, "t", "p", "u", 10, true, "attachment-upload-request"), "https://audiences.afriland.cm", "STANDARD", false, java.time.Duration.ofMinutes(5));
    }

    @BeforeEach
    void preparer() {
        service = new AccuseNotificationService(accuseRepository, envoiRepository, proprietes());
        lenient().when(accuseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AccuseEngine succes(String serviceId) {
        return new AccuseEngine("019ed46d-c20a-7074", "wf-1", serviceId, "SMS", "ozeki", "SM123",
                null, null, INSTANT, null, null);
    }

    private static NotificationEnvoi envoiPublie(String eventId) {
        NotificationEnvoi envoi = NotificationEnvoi.aRemettre(
                new Origine(eventId, "RAPPEL_AUDIENCE", 5L, 42L, TypeDestinataire.AVOCAT, 9L),
                CanalEngine.SMS, "+237600000000", null, "Texte");
        envoi.marquerPubliee("DAPR", "+237600000000");
        return envoi;
    }

    // Tracking topics

    @Test
    void un_accuse_de_notre_service_est_enregistre_avec_son_fournisseur() {
        assertThat(service.enregistrer(succes("audience-deliberer"), TypeAccuse.SENT)).isTrue();

        ArgumentCaptor<NotificationAccuse> captor = ArgumentCaptor.forClass(NotificationAccuse.class);
        verify(accuseRepository).save(captor.capture());
        NotificationAccuse accuse = captor.getValue();
        assertThat(accuse.getNotificationId()).isEqualTo("019ed46d-c20a-7074");
        assertThat(accuse.getType()).isEqualTo(TypeAccuse.SENT);
        assertThat(accuse.getCanal()).isEqualTo("SMS");
        assertThat(accuse.getFournisseur()).isEqualTo("ozeki");
        assertThat(accuse.getDateEvenement()).isNotNull();
        // The ack does not carry our reference, so it stays unlinked.
        assertThat(accuse.getNotificationEnvoiId()).isNull();
    }

    /** The topics carry acks for all the bank's services (Guide V1 section 8). */
    @Test
    void un_accuse_d_un_autre_service_est_ignore() {
        assertThat(service.enregistrer(succes("mobile-banking"), TypeAccuse.SENT)).isFalse();
        verify(accuseRepository, never()).save(any());
    }

    /** At-least-once delivery: a duplicate is normal and has no effect. */
    @Test
    void un_accuse_deja_recu_n_est_pas_enregistre_deux_fois() {
        when(accuseRepository.existsByNotificationIdAndType("019ed46d-c20a-7074", TypeAccuse.SENT)).thenReturn(true);

        assertThat(service.enregistrer(succes("audience-deliberer"), TypeAccuse.SENT)).isFalse();
        verify(accuseRepository, never()).save(any());
    }

    @Test
    void un_accuse_sans_identifiant_est_ignore() {
        AccuseEngine sansId = new AccuseEngine(null, null, "audience-deliberer", "SMS", "ozeki", null, null, null,
                INSTANT, null, null);

        assertThat(service.enregistrer(sansId, TypeAccuse.SENT)).isFalse();
        assertThat(service.enregistrer(null, TypeAccuse.FAILED)).isFalse();
    }

    /** If the engine ever added our reference, linking would happen automatically. */
    @Test
    void un_accuse_portant_notre_reference_rattache_et_marque_la_ligne_envoyee() {
        AccuseEngine avecReference = new AccuseEngine("019ed46d", "wf", "audience-deliberer", "SMS", "ozeki", "SM1",
                null, null, INSTANT, null, "alerte-42:SMS");
        NotificationEnvoi envoi = envoiPublie("alerte-42:SMS");
        when(envoiRepository.findByEventId("alerte-42:SMS")).thenReturn(Optional.of(envoi));

        service.enregistrer(avecReference, TypeAccuse.SENT);

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ENVOYEE);
        assertThat(envoi.getStatutEngine()).isEqualTo("ozeki");
    }

    // Engine callback (callbackUrl), currently the only exact path

    @Test
    void un_rappel_sans_erreur_marque_la_ligne_envoyee_et_rattache_l_accuse() {
        NotificationEnvoi envoi = envoiPublie("alerte-50:SMS");
        when(envoiRepository.findByEventId("alerte-50:SMS")).thenReturn(Optional.of(envoi));

        service.enregistrerRappel("alerte-50:SMS", succes("audience-deliberer"));

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ENVOYEE);
        ArgumentCaptor<NotificationAccuse> captor = ArgumentCaptor.forClass(NotificationAccuse.class);
        verify(accuseRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeAccuse.SENT);
    }

    @Test
    void un_rappel_sans_corps_vaut_succes_et_reste_tracable() {
        NotificationEnvoi envoi = envoiPublie("alerte-51:SMS");
        when(envoiRepository.findByEventId("alerte-51:SMS")).thenReturn(Optional.of(envoi));

        service.enregistrerRappel("alerte-51:SMS", null);

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ENVOYEE);
        assertThat(envoi.getStatutEngine()).isEqualTo("CALLBACK");
    }

    @Test
    void un_rappel_en_erreur_met_la_ligne_en_echec_sans_nouvelle_tentative() {
        AccuseEngine echec = new AccuseEngine("019ed470", "wf", "audience-deliberer", "EMAIL", "smtp-drh", null,
                "Connection refused", true, null, INSTANT, null);
        NotificationEnvoi envoi = envoiPublie("alerte-52:EMAIL");
        when(envoiRepository.findByEventId("alerte-52:EMAIL")).thenReturn(Optional.of(envoi));

        service.enregistrerRappel("alerte-52:EMAIL", echec);

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ECHEC);
        assertThat(envoi.getDerniereErreur()).isEqualTo("Connection refused");
        assertThat(envoi.getProchaineTentative()).isNull();
    }

    @Test
    void un_rappel_pour_une_reference_inconnue_ne_casse_rien() {
        when(envoiRepository.findByEventId("inconnue")).thenReturn(Optional.empty());

        service.enregistrerRappel("inconnue", succes("audience-deliberer"));

        verify(accuseRepository, never()).save(any());
    }
}
