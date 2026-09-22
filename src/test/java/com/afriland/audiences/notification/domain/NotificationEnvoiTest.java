package com.afriland.audiences.notification.domain;

import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEnvoiTest {

    private static final Origine ORIGINE =
            new Origine("alerte-42", "RAPPEL_AUDIENCE", 5L, 42L, TypeDestinataire.UTILISATEUR, 7L);

    @Test
    void aRemettre_est_en_attente_et_immediatement_eligible() {
        NotificationEnvoi envoi = NotificationEnvoi.aRemettre(ORIGINE, CanalEngine.TEAMS,
                "paul_fokam@afrilandfirstbank.com", "Rappel", "Audience dans 7 jours.");

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(envoi.getTentatives()).isZero();
        assertThat(envoi.getProchaineTentative()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(envoi.getDossierId()).isEqualTo(5L);
        assertThat(envoi.getAlerteId()).isEqualTo(42L);
        assertThat(envoi.getDestinataireRef()).isEqualTo(7L);
    }

    @Test
    void le_canal_complete_l_eventId_pour_que_les_canaux_ne_se_dedoublonnent_pas() {
        NotificationEnvoi teams = NotificationEnvoi.aRemettre(ORIGINE, CanalEngine.TEAMS, "a@b.cm", null, "x");
        NotificationEnvoi courriel = NotificationEnvoi.aRemettre(ORIGINE, CanalEngine.EMAIL, "a@b.cm", "s", "x");

        assertThat(teams.getEventId()).isEqualTo("alerte-42:TEAMS");
        assertThat(courriel.getEventId()).isEqualTo("alerte-42:EMAIL");
    }

    @Test
    void aRemettre_refuse_un_destinataire_ou_un_corps_absent() {
        assertThatThrownBy(() -> NotificationEnvoi.aRemettre(ORIGINE, CanalEngine.SMS, " ", null, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonJoignable");
        assertThatThrownBy(() -> NotificationEnvoi.aRemettre(ORIGINE, CanalEngine.SMS, "237654476789", null, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonJoignable_n_a_ni_destinataire_ni_echeance() {
        NotificationEnvoi envoi = NotificationEnvoi.nonJoignable(ORIGINE, CanalEngine.SMS, "COORDONNEE_ABSENTE");

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.NON_JOIGNABLE);
        assertThat(envoi.getDerniereErreur()).isEqualTo("COORDONNEE_ABSENTE");
        assertThat(envoi.getDestinataire()).isNull();
        assertThat(envoi.getProchaineTentative()).isNull();
    }

    @Test
    void marquerEnvoyee_efface_le_texte_des_sms_et_whatsapp() {
        for (CanalEngine canal : new CanalEngine[] {CanalEngine.SMS, CanalEngine.WHATSAPP}) {
            NotificationEnvoi envoi = NotificationEnvoi.aRemettre(ORIGINE, canal, "237654476789", null, "Texte");
            LocalDateTime accuse = LocalDateTime.now();

            envoi.marquerEnvoyee("DELIVERED", accuse);

            assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ENVOYEE);
            assertThat(envoi.getStatutEngine()).isEqualTo("DELIVERED");
            assertThat(envoi.getDateAccuse()).isEqualTo(accuse);
            assertThat(envoi.getProchaineTentative()).isNull();
            assertThat(envoi.getCorps()).as(canal.name()).isNull();
        }
    }

    @Test
    void marquerEnvoyee_conserve_le_texte_des_courriels_et_messages_teams() {
        for (CanalEngine canal : new CanalEngine[] {CanalEngine.EMAIL, CanalEngine.TEAMS}) {
            NotificationEnvoi envoi = NotificationEnvoi.aRemettre(ORIGINE, canal, "a@b.cm", "Sujet", "Texte");

            envoi.marquerEnvoyee("SENT", LocalDateTime.now());

            assertThat(envoi.getCorps()).as(canal.name()).isEqualTo("Texte");
        }
    }

    @Test
    void origine_exige_eventId_type_et_nature_du_destinataire() {
        assertThatThrownBy(() -> new Origine(" ", "T", null, null, TypeDestinataire.AVOCAT, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Origine("e", " ", null, null, TypeDestinataire.AVOCAT, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Origine("e", "T", null, null, null, 1L))
                .isInstanceOf(NullPointerException.class);
    }
}
