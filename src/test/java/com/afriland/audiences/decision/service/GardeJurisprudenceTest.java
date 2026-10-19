package com.afriland.audiences.decision.service;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notion of a "closed" step on which the case-law guard relies.
 *
 * <p>A decision enters the case-law base only once final, with no step of the case still in
 * progress. This tests the boundary itself: which statuses count as finished.
 */
class GardeJurisprudenceTest {

    @Test
    void seules_la_cloture_et_l_archive_ferment_une_etape() {
        assertThat(StatutCycleVie.CLOTURE.estFerme()).isTrue();
        assertThat(StatutCycleVie.ARCHIVE.estFerme()).isTrue();
    }

    /**
     * A delivered deliberation is <b>not</b> an end: the appeal period may run and the step may reopen.
     * Filing a decision at that point is exactly what the guard prevents.
     */
    @Test
    void un_delibere_vide_ne_ferme_pas_l_etape() {
        assertThat(StatutCycleVie.DELIBERE_VIDE.estFerme()).isFalse();
    }

    @Test
    void aucun_statut_anterieur_ne_ferme_l_etape() {
        assertThat(StatutCycleVie.OUVERTURE.estFerme()).isFalse();
        assertThat(StatutCycleVie.EN_COURS.estFerme()).isFalse();
        assertThat(StatutCycleVie.EN_DELIBERE.estFerme()).isFalse();
    }

    @Test
    void la_liste_des_statuts_fermes_est_celle_des_statuts_qui_ferment() {
        List<StatutCycleVie> fermes = java.util.Arrays.stream(StatutCycleVie.values())
                .filter(StatutCycleVie::estFerme).toList();

        assertThat(StatutCycleVie.FERMES).isEqualTo(fermes);
    }

    /** A status added later without an explicit decision counts as open: the safe default. */
    @Test
    void tout_statut_hors_de_la_liste_compte_comme_ouvert() {
        for (StatutCycleVie statut : StatutCycleVie.values()) {
            assertThat(statut.estFerme()).isEqualTo(StatutCycleVie.FERMES.contains(statut));
        }
    }
}
