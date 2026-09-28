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

    /** Les deux cassations sont concurrentes : aucune ne suit l'autre, aucune n'ouvre de suite. */
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
     * Le pourvoi CCJA n'est jamais ouvert automatiquement : il ne suit aucune étape. Le vérifier
     * ici évite qu'une future valeur ajoutée à l'énumération ne s'y glisse par distraction.
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
     * Le tri des étapes d'un dossier est alphabétique sur la colonne `type` : l'ordre procédural
     * n'en découle que si les noms sont choisis pour cela. Ce test fige ce lien, invisible partout
     * ailleurs — une valeur mal nommée fausserait l'étape courante sans rien casser.
     */
    @Test
    void l_ordre_alphabetique_des_noms_suit_l_ordre_procedural() {
        List<String> noms = Arrays.stream(TypeEtape.values()).map(Enum::name).toList();
        assertThat(noms).isSorted();
    }
}
