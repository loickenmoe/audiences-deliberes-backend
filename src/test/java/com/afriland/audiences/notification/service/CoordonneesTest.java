package com.afriland.audiences.notification.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CoordonneesTest {

    /**
     * Une seule écriture est enregistrée, le format international, d'où que parte la saisie : trois
     * façons d'écrire le même numéro donneraient trois valeurs incomparables en base.
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

    /** Les mobiles Camtel commencent eux aussi par 6 : « +237 6 20 XX XX XX ». */
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

    /** Les lignes fixes, en 2, ne reçoivent ni SMS ni WhatsApp : elles sont écartées. */
    @ParameterizedTest
    @ValueSource(strings = {"222228936", "2 22 22 89 36", "+237 2 22 22 89 36", "237222228936"})
    void telephone_lignesFixes_sontRefusees(String saisie) {
        assertThat(Coordonnees.telephone(saisie)).isEmpty();
    }

    /**
     * Ni plus, ni moins de chiffres. Un numéro camerounais amputé ou allongé est refusé, <b>y compris
     * écrit avec l'indicatif</b> : sans cette garde, la règle internationale le rattraperait et le
     * message partirait vers quelqu'un d'autre.
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
     * Les prestataires étrangers existent et le moteur délivre au-delà du Cameroun : l'indicatif est
     * alors obligatoire. Sans lui, il faudrait le deviner — et deviner un numéro, c'est écrire à
     * quelqu'un d'autre.
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
}
