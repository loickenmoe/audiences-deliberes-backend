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
            "'654476789', 237654476789",
            "'654 47 67 89', 237654476789",
            "'654-47-67-89', 237654476789",
            "'237654476789', 237654476789",
            "'237 654 476 789', 237654476789",
            "'+237654476789', +237654476789",
            "'+237 (654) 47.67.89', +237654476789",
            "'+33612345678', +33612345678"
    })
    void telephone_accepteLesFormesDuGuide(String saisie, String attendu) {
        assertThat(Coordonnees.telephone(saisie)).contains(attendu);
    }

    /** Rien n'est deviné : un numéro hors des trois formes ne part pas. */
    @ParameterizedTest
    @ValueSource(strings = {"54476789", "754476789", "0654476789", "23765447678", "+0237654476789", "abc", "654476789x"})
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
    @CsvSource({"' loicluc25@gmail.com ', loicluc25@gmail.com", "paul_fokam@afrilandfirstbank.com, paul_fokam@afrilandfirstbank.com"})
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
