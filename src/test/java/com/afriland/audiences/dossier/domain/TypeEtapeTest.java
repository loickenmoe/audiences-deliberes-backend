package com.afriland.audiences.dossier.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeEtapeTest {

    @Test
    void suivante_followsTheDocumentedProgression() {
        assertThat(TypeEtape.INSTANCE.suivante()).isEqualTo(TypeEtape.RECOURS_1);
        assertThat(TypeEtape.RECOURS_1.suivante()).isEqualTo(TypeEtape.RECOURS_2);
    }

    @Test
    void suivante_throwsPastRecours2() {
        assertThatThrownBy(TypeEtape.RECOURS_2::suivante).isInstanceOf(IllegalStateException.class);
    }
}
