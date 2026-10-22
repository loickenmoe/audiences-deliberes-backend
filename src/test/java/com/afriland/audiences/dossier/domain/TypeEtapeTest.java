package com.afriland.audiences.dossier.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeEtapeTest {

    @Test
    void suivante_followsTheDocumentedProgression() {
        assertThat(TypeEtape.INSTANCE.suivante()).isEqualTo(TypeEtape.RECOURS_1);
        assertThat(TypeEtape.RECOURS_1.suivante()).isEqualTo(TypeEtape.RECOURS_2);
    }

    /** The two cassation appeals run concurrently: neither follows the other, neither opens a next step. */
    @Test
    void aucune_etape_ne_suit_les_deux_pourvois() {
        assertThatThrownBy(TypeEtape.RECOURS_2::suivante).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(TypeEtape.RECOURS_2_CCJA::suivante).isInstanceOf(IllegalStateException.class);
        assertThat(TypeEtape.RECOURS_2.estTerminale()).isTrue();
        assertThat(TypeEtape.RECOURS_2_CCJA.estTerminale()).isTrue();
        assertThat(TypeEtape.INSTANCE.estTerminale()).isFalse();
        assertThat(TypeEtape.RECOURS_1.estTerminale()).isFalse();
    }

    /**
     * The CCJA appeal is never opened automatically: it follows no step. Guards against a future
     * enum value slipping in.
     */
    @Test
    void aucune_etape_n_ouvre_automatiquement_le_pourvoi_ccja() {
        for (TypeEtape type : TypeEtape.values()) {
            if (type.estTerminale()) {
                continue;
            }
            assertThat(type.suivante()).isNotEqualTo(TypeEtape.RECOURS_2_CCJA);
        }
    }

    /**
     * Steps are sorted alphabetically on the `type` column, so the procedural order only holds if
     * the names are chosen for it. A badly named value would silently skew the current step.
     */
    @Test
    void l_ordre_alphabetique_des_noms_suit_l_ordre_procedural() {
        List<String> noms = Arrays.stream(TypeEtape.values()).map(Enum::name).toList();
        assertThat(noms).isSorted();
    }
}
