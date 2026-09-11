package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtapeTransitionValidatorTest {

    private final EtapeTransitionValidator validator = new EtapeTransitionValidator();

    @ParameterizedTest
    @CsvSource({
            "OUVERTURE, EN_COURS, true",
            "EN_COURS, EN_DELIBERE, true",
            "DELIBERE_VIDE, CLOTURE, true",
            "DELIBERE_VIDE, EN_COURS, true",
            "CLOTURE, ARCHIVE, true",
            // Réservées aux endpoints dédiés du module délibéré (M7) :
            "EN_DELIBERE, DELIBERE_VIDE, false",
            "EN_DELIBERE, EN_COURS, false",
            // Sauts et retours interdits :
            "OUVERTURE, EN_DELIBERE, false",
            "OUVERTURE, CLOTURE, false",
            "EN_COURS, CLOTURE, false",
            "ARCHIVE, EN_COURS, false",
            "CLOTURE, EN_COURS, false",
            "OUVERTURE, OUVERTURE, false",
    })
    void estAutorisee_matchesTheDocumentedTransitionTable(StatutCycleVie de, StatutCycleVie vers, boolean attendu) {
        assertThat(validator.estAutorisee(de, vers)).isEqualTo(attendu);
    }

    @Test
    void estExerciceDeRecours_trueOnlyForDelibereVideToEnCours() {
        assertThat(validator.estExerciceDeRecours(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isTrue();
        assertThat(validator.estExerciceDeRecours(StatutCycleVie.OUVERTURE, StatutCycleVie.EN_COURS)).isFalse();
        assertThat(validator.estExerciceDeRecours(StatutCycleVie.EN_DELIBERE, StatutCycleVie.EN_COURS)).isFalse();
    }
}
