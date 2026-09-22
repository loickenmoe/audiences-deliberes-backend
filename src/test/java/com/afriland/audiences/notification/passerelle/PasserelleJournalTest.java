package com.afriland.audiences.notification.passerelle;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasserelleJournalTest {

    @Test
    void publier_n_echoue_jamais() {
        DemandeNotification demande = new DemandeNotification("audience-5-J-7",
                Map.of(CanalEngine.SMS, "237654476789", CanalEngine.EMAIL, "loicluc25@gmail.com"),
                "Rappel d'audience", "Audience dans 7 jours.");

        assertThatCode(() -> new PasserelleJournal().publier(demande)).doesNotThrowAnyException();
    }

    @Test
    void masquer_cache_les_coordonnees_personnelles() {
        assertThat(PasserelleJournal.masquer("paul_fokam@afrilandfirstbank.com")).isEqualTo("pa***@afrilandfirstbank.com");
        assertThat(PasserelleJournal.masquer("237654476789")).isEqualTo("23***");
        assertThat(PasserelleJournal.masquer("ab")).isEqualTo("***");
        assertThat(PasserelleJournal.masquer(" ")).isEqualTo("(vide)");
    }

    @Test
    void masquer_liste_les_canaux_dans_un_ordre_stable() {
        String masque = PasserelleJournal.masquer(Map.of(
                CanalEngine.TEAMS, "paul_fokam@afrilandfirstbank.com",
                CanalEngine.SMS, "237654476789"));

        assertThat(masque).isEqualTo("{SMS=23***, TEAMS=pa***@afrilandfirstbank.com}");
        assertThat(masque).doesNotContain("654476789", "paul_fokam");
    }

    @Test
    void demande_refuse_un_eventId_absent_car_la_deduplication_en_depend() {
        assertThatThrownBy(() -> new DemandeNotification(" ", Map.of(CanalEngine.SMS, "237654476789"), null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void demande_refuse_une_absence_de_canal_ou_de_corps() {
        assertThatThrownBy(() -> new DemandeNotification("e1", Map.of(), null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemandeNotification("e1", Map.of(CanalEngine.SMS, "237654476789"), null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
