package com.afriland.audiences.notification.passerelle;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PasserelleJournalTest {

    private static final MessageEngine MESSAGE = MessageEngine.de("jeton", "audience-deliberer", "STANDARD", CanalEngine.SMS, "237600000000",
            null, "Audience dans 7 jours.", "alerte-5-SMS", null);

    @Test
    void publier_n_envoie_rien_et_nomme_son_transport() {
        assertThat(new PasserelleJournal().publier(MESSAGE)).isEqualTo("JOURNAL");
    }

    @Test
    void masquer_cache_les_coordonnees_personnelles() {
        assertThat(PasserelleJournal.masquer("juriste.test@afrilandfirstbank.com"))
                .isEqualTo("ju***@afrilandfirstbank.com");
        assertThat(PasserelleJournal.masquer("237600000000")).isEqualTo("23***");
        assertThat(PasserelleJournal.masquer("ab")).isEqualTo("***");
        assertThat(PasserelleJournal.masquer(" ")).isEqualTo("(vide)");
    }

    @Test
    void masquer_liste_les_canaux_dans_un_ordre_stable() {
        String masque = PasserelleJournal.masquer(Map.of(
                "TEAMS", "juriste.test@afrilandfirstbank.com",
                "SMS", "237600000000"));

        assertThat(masque).isEqualTo("{SMS=23***, TEAMS=ju***@afrilandfirstbank.com}");
        assertThat(masque).doesNotContain("600000000", "juriste.test");
    }

    /** Le jeton porte la signature du service : il ne doit jamais être journalisé. */
    @Test
    void le_message_journalise_est_depouille_de_son_jeton() {
        assertThat(MESSAGE.token()).isEqualTo("jeton");
        assertThat(MESSAGE.sansJeton().token()).isNull();
        assertThat(MESSAGE.sansJeton().directBody()).isEqualTo("Audience dans 7 jours.");
    }

    /** Le Guide ignore {@code recipient} quand {@code channelRecipients} est présent : on n'utilise que ce dernier. */
    @Test
    void le_message_porte_un_destinataire_par_canal() {
        assertThat(MESSAGE.channels()).containsExactly("SMS");
        assertThat(MESSAGE.recipient()).isEqualTo("237600000000");
        assertThat(MESSAGE.directSubject()).isNull();
    }
}
