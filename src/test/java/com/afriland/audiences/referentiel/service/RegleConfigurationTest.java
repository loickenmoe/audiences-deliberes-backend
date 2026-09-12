package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Q-91 — règles de saisie des paramètres système. */
class RegleConfigurationTest {

    @ParameterizedTest
    @CsvSource({
            "SEUIL_MONTANT_DEFAUT, ' 75000000 ', 75000000",
            "SEUIL_PROROGATIONS, '12', 12",
            "TAILLE_MAX_GED, '5242880', 5242880",
            "FORMATS_GED, ' pdf , xlsx , pdf ', 'PDF,XLSX'",
            "CANAUX_NOTIFICATION_DEFAUT, 'applicatif,outlook', 'APPLICATIF,OUTLOOK'"
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
            "DELAI_VALIDATION_CONJOINTE, '2000000000'",
            "TAILLE_MAX_GED, '104857600'",
            "FORMATS_GED, 'PDF,DOCX'",
            "FORMATS_GED, 'PDF,,PNG'",
            "CANAUX_NOTIFICATION_DEFAUT, 'OUTLOOK'"
    })
    void valider_rejectsValuesThatWouldBreakAConsumer(String cle, String saisie) {
        assertThatThrownBy(() -> RegleConfiguration.valueOf(cle).valider(saisie))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(cle);
    }

    @Test
    void valider_rejectsBlankValue() {
        assertThatThrownBy(() -> RegleConfiguration.SEUIL_PROROGATIONS.valider("   "))
                .isInstanceOf(ApiException.class);
    }

    /** TAILLE_MAX_GED est plafonnée à la limite multipart de Spring : au-delà, le fichier est rejeté avant le contrôle métier. */
    @Test
    void tailleMaxGed_isCappedAtTheSpringMultipartLimit() {
        assertThat(RegleConfiguration.TAILLE_MAX_GED.maximum()).isEqualTo(10_485_760L);
    }

    /** Chaque clé décrit sa saisie à l'écran d'administration : bornes pour un entier, valeurs pour une liste. */
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
