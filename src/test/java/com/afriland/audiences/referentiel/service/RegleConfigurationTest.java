package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Input rules for system parameters. */
class RegleConfigurationTest {

    @ParameterizedTest
    @CsvSource({
            "SEUIL_MONTANT_DEFAUT, ' 75000000 ', 75000000",
            "SEUIL_PROROGATIONS, '12', 12",
            "TAILLE_MAX_GED, '5242880', 5242880",
            "FORMATS_GED, ' pdf , xlsx , pdf ', 'PDF,XLSX'",
            "FORMATS_GED, ' docx , doc ', 'DOCX,DOC'",
            "CANAUX_NOTIFICATION_DEFAUT, 'applicatif,outlook', 'APPLICATIF,OUTLOOK'",
            "CANAUX_NOTIFICATION_PRESTATAIRES, ' sms , courriel_personnel ', 'SMS,COURRIEL_PERSONNEL'",
            "GRILLE_NOTIFICATION_TEAMS, 'rappel_audience,delai_recours', 'RAPPEL_AUDIENCE,DELAI_RECOURS'",
            "GRILLE_NOTIFICATION_SMS, 'affectation_dossier', 'AFFECTATION_DOSSIER'"
    })
    void valider_normalizesAcceptedValues(String cle, String saisie, String attendu) {
        assertThat(RegleConfiguration.valueOf(cle).valider(saisie)).isEqualTo(attendu);
    }

    @ParameterizedTest
    @CsvSource({
            "SEUIL_MONTANT_DEFAUT, '50 000 000'",
            "SEUIL_MONTANT_DEFAUT, 'beaucoup'",
            "SEUIL_MONTANT_DEFAUT, '-1'",
            "SEUIL_PROROGATIONS, '0'",
            "SEUIL_PROROGATIONS, '1.5'",
            "DELAI_VALIDATION_CONJOINTE, '3000000000'",
            "TAILLE_MAX_GED, '104857600'",
            "FORMATS_GED, 'PDF,ODT'",
            "FORMATS_GED, 'PDF,,PNG'",
            "CANAUX_NOTIFICATION_DEFAUT, 'OUTLOOK'",
            "CANAUX_NOTIFICATION_DEFAUT, ''",
            "CANAUX_NOTIFICATION_PRESTATAIRES, 'TEAMS'",
            "GRILLE_NOTIFICATION_OUTLOOK, 'AFFECTATION_DOSSIER'",
            "GRILLE_NOTIFICATION_SMS, 'DOUBLON_AUDIENCE'",
            "GRILLE_NOTIFICATION_WHATSAPP, 'SMS,,RAPPEL_AUDIENCE'"
    })
    void valider_rejectsValuesThatWouldBreakAConsumer(String cle, String saisie) {
        RegleConfiguration regle = RegleConfiguration.valueOf(cle);
        assertThatThrownBy(() -> regle.valider(saisie))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(cle);
    }

    /**
     * Disabling all provider channels, or sending nothing on a channel, is a legitimate setting. These
     * lists accept empty values; the others always refuse them.
     */
    @ParameterizedTest
    @EnumSource(value = RegleConfiguration.class, names = {"CANAUX_NOTIFICATION_PRESTATAIRES",
            "GRILLE_NOTIFICATION_OUTLOOK", "GRILLE_NOTIFICATION_TEAMS", "GRILLE_NOTIFICATION_COURRIEL_PERSONNEL",
            "GRILLE_NOTIFICATION_SMS", "GRILLE_NOTIFICATION_WHATSAPP"})
    void valider_acceptsEmptyNotificationLists(RegleConfiguration regle) {
        assertThat(regle.videAutorise()).isTrue();
        assertThat(regle.valider("  ")).isEmpty();
        assertThat(regle.valider(null)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = RegleConfiguration.class, names = {"FORMATS_GED", "CANAUX_NOTIFICATION_DEFAUT", "SEUIL_PROROGATIONS"})
    void valider_stillRejectsEmptyValueElsewhere(RegleConfiguration regle) {
        assertThat(regle.videAutorise()).isFalse();
        assertThatThrownBy(() -> regle.valider("")).isInstanceOf(ApiException.class);
    }

    @Test
    void valider_rejectsBlankValue() {
        assertThatThrownBy(() -> RegleConfiguration.SEUIL_PROROGATIONS.valider("   "))
                .isInstanceOf(ApiException.class);
    }

    /** CONF06: 10 MB, a parameter owned by IT through Spring Boot / MinIO configuration. Beyond it, Spring rejects the file before the business check. */
    @Test
    void tailleMaxGed_isCappedAtTheSpringMultipartLimit() {
        assertThat(RegleConfiguration.TAILLE_MAX_GED.maximum()).isEqualTo(10_485_760L);
    }

    /**
     * The sources set no business maximum for counters (US 7.1: "threshold (positive integer)"). An
     * absurd but harmless value is accepted: validation refuses what would break the application.
     */
    @ParameterizedTest
    @CsvSource({ "SEUIL_PROROGATIONS, '999999'", "CHARGE_MAX_AVOCAT, '100000'", "DELAI_VALIDATION_CONJOINTE, '3650'" })
    void valider_acceptsLargeButHarmlessCounters(String cle, String saisie) {
        assertThat(RegleConfiguration.valueOf(cle).valider(saisie)).isEqualTo(saisie);
    }

    /** Each key describes its input on the administration screen: bounds for an integer, values for a list. */
    @ParameterizedTest
    @EnumSource(RegleConfiguration.class)
    void chaqueRegle_describesItsInput(RegleConfiguration regle) {
        if (regle.type() == TypeValeurConfiguration.ENTIER) {
            assertThat(regle.minimum()).isNotNull();
            assertThat(regle.maximum()).isNotNull().isGreaterThan(regle.minimum());
            assertThat(regle.valeursPossibles()).isEmpty();
        } else {
            assertThat(regle.valeursPossibles()).isNotEmpty();
            assertThat(regle.minimum()).isNull();
        }
    }
}
