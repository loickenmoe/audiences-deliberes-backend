package com.afriland.audiences.decision.service;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La notion d'étape « fermée », sur laquelle repose la garde jurisprudentielle (2026-09-29).
 *
 * <p>Une décision ne rejoint la base que lorsqu'elle est définitive — plus aucune étape du dossier
 * en cours. Ce qui se teste ici est la frontière elle-même : quels statuts comptent comme terminés.
 * Elle était jusqu'ici recopiée dans {@code RepertoireAvocatsService} et {@code ReportingService},
 * sans que rien ne garantisse que les trois copies disent la même chose.
 */
class GardeJurisprudenceTest {

    @Test
    void seules_la_cloture_et_l_archive_ferment_une_etape() {
        assertThat(StatutCycleVie.CLOTURE.estFerme()).isTrue();
        assertThat(StatutCycleVie.ARCHIVE.estFerme()).isTrue();
    }

    /**
     * Le délibéré vidé <b>n'est pas</b> une fin : le délai de recours peut courir, et l'étape se
     * rouvrir. Verser une décision à ce moment-là serait exactement le défaut que la garde vise.
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

    /** Un statut ajouté demain sans décision explicite compterait comme ouvert : c'est le bon défaut. */
    @Test
    void tout_statut_hors_de_la_liste_compte_comme_ouvert() {
        for (StatutCycleVie statut : StatutCycleVie.values()) {
            assertThat(statut.estFerme()).isEqualTo(StatutCycleVie.FERMES.contains(statut));
        }
    }
}
