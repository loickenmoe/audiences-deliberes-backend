package com.afriland.audiences.notification.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CoordonneesTest {

    /** Les trois formes du Guide V1, avec les séparateurs de saisie courants. */
    @ParameterizedTest
    @CsvSource({
            "'600000000', 237600000000",
            "'600 00 00 00', 237600000000",
            "'600-00-00-00', 237600000000",
            "'237600000000', 237600000000",
            "'237 600 000 000', 237600000000",
            "'+237600000000', +237600000000",
            "'+237 (600) 00.00.00', +237600000000",
            "'+33612345678', +33612345678"
    })
    void telephone_accepteLesFormesDuGuide(String saisie, String attendu) {
        assertThat(Coordonnees.telephone(saisie)).contains(attendu);
    }

    /** Rien n'est deviné : un numéro hors des trois formes ne part pas. */
    @ParameterizedTest
    @ValueSource(strings = {"54476789", "754476789", "0600000000", "23765447678", "+0237600000000", "abc", "600000000x"})
    void telephone_refuseCeQueLeGuideNAccepteLePas(String saisie) {
        assertThat(Coordonnees.telephone(saisie)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void telephone_absent(String saisie) {
        assertThat(Coordonnees.telephone(saisie)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"' prestataire.test@example.cm ', prestataire.test@example.cm", "juriste.test@afrilandfirstbank.com, juriste.test@afrilandfirstbank.com"})
    void courriel_accepteUneAdresseBienFormee(String saisie, String attendu) {
        assertThat(Coordonnees.courriel(saisie)).contains(attendu);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sans-arobase", "a@b", "a b@c.cm", "a@@b.cm"})
    void courriel_refuseUneAdresseMalFormee(String saisie) {
        assertThat(Coordonnees.courriel(saisie)).isEmpty();
    }
}
