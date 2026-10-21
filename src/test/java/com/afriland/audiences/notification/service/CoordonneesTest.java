package com.afriland.audiences.notification.service;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CoordonneesTest {

    /**
     * A single format is stored (international), whatever the input: three ways of writing the same
     * number would give three incomparable values in the database.
     */
    @ParameterizedTest
    @CsvSource({
            "'650228936', +237650228936",
            "'650 228 936', +237650228936",
            "'650-228-936', +237650228936",
            "'237650228936', +237650228936",
            "'237 650 228 936', +237650228936",
            "'+237650228936', +237650228936",
            "'+237 650 228 936', +237650228936",
            "'+237 (650) 22.89.36', +237650228936"
    })
    void telephone_camerounais_toujoursEnFormatInternational(String saisie, String attendu) {
        assertThat(Coordonnees.telephone(saisie)).contains(attendu);
    }

    /** Camtel mobiles also start with 6: "+237 6 20 XX XX XX". */
    @ParameterizedTest
    @CsvSource({
            "'620228936', +237620228936",
            "'6 20 22 89 36', +237620228936",
            "'+237 6 20 22 89 36', +237620228936",
            "'237620228936', +237620228936"
    })
    void telephone_mobilesCamtel_sontAcceptes(String saisie, String attendu) {
        assertThat(Coordonnees.telephone(saisie)).contains(attendu);
    }

    /** Landlines (starting with 2) receive neither SMS nor WhatsApp: they are rejected. */
    @ParameterizedTest
    @ValueSource(strings = {"222228936", "2 22 22 89 36", "+237 2 22 22 89 36", "237222228936"})
    void telephone_lignesFixes_sontRefusees(String saisie) {
        assertThat(Coordonnees.telephone(saisie)).isEmpty();
    }

    /**
     * Exactly the right number of digits. A truncated or extended Cameroonian number is refused,
     * <b>even with the country code</b>: otherwise the international rule would accept it and the message
     * would reach someone else.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "65022893", "6502289361", "+23765022893", "+2376502289361", "23765022893", "237 650 228 93",
            "0650228936", "750228936", "350228936", "abc", "650228936x"
    })
    void telephone_camerounais_horsFormat_estRefuse(String saisie) {
        assertThat(Coordonnees.telephone(saisie)).isEmpty();
    }

    /**
     * Foreign providers exist and the engine delivers beyond Cameroon: the country code is then
     * mandatory. Guessing it would mean writing to someone else.
     */
    @ParameterizedTest
    @CsvSource({
            "'+33612345678', +33612345678",
            "'+33 6 12 34 56 78', +33612345678",
            "'+241 01 23 45 67', +24101234567",
            "'+1 202 555 0143', +12025550143"
    })
    void telephone_etranger_exigeLIndicatif(String saisie, String attendu) {
        assertThat(Coordonnees.telephone(saisie)).contains(attendu);
    }

    @ParameterizedTest
    @ValueSource(strings = {"33612345678", "0033612345678", "+0612345678", "+331", "+3361234567890123456"})
    void telephone_etranger_sansIndicatifOuMalForme_estRefuse(String saisie) {
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

    /** Edge cases of the domain part, pinned so the expression keeps accepting exactly the same set. */
    @ParameterizedTest
    @ValueSource(strings = {"x@.a.b", "x@a..", "x@a.b.c", "x@a..b", "x@a-b.c", "x.y@a.b"})
    void courriel_domaineAvecUnPointNiInitialNiFinal_estAccepte(String saisie) {
        assertThat(Coordonnees.courriel(saisie)).contains(saisie);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x@a.", "x@.a", "x@.", "x@..", "x@ab", "@a.b", "x@a.b@c", "x@a.\tb"})
    void courriel_domaineSansPointInterieur_estRefuse(String saisie) {
        assertThat(Coordonnees.courriel(saisie)).isEmpty();
    }

    @Test
    void courriel_longueSaisieMalFormee_estTraiteeEnTempsLineaire() {
        String saisie = "a@" + "a.".repeat(50_000) + "@";
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThat(Coordonnees.courriel(saisie)).isEmpty());
    }
}
