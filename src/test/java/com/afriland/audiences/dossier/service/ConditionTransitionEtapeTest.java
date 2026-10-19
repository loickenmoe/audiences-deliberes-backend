package com.afriland.audiences.dossier.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The factual-condition mechanism (each real condition is tested in the module that provides it).
 * Checks what no module can check alone: an unrelated condition stays silent, several refusals are
 * returned <b>together</b>, and a refusal always carries a readable reason.
 */
class ConditionTransitionEtapeTest {

    /** A test condition: applies to a given transition and always or never refuses. */
    private static ConditionTransitionEtape condition(StatutCycleVie de, StatutCycleVie vers, String motif) {
        return new ConditionTransitionEtape() {
            @Override
            public boolean concerne(StatutCycleVie depuis, StatutCycleVie versStatut) {
                return depuis == de && versStatut == vers;
            }

            @Override
            public Optional<String> empechement(EtapeProcedure etape) {
                return Optional.ofNullable(motif);
            }
        };
    }

    private static List<String> empechements(List<ConditionTransitionEtape> conditions,
                                             StatutCycleVie de, StatutCycleVie vers) {
        return conditions.stream()
                .filter(condition -> condition.concerne(de, vers))
                .map(condition -> condition.empechement(EtapeProcedure.builder().build()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Test
    void une_condition_qui_ne_concerne_pas_le_passage_ne_dit_rien() {
        var conditions = List.of(
                condition(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE, "Il manque une audience."));

        assertThat(empechements(conditions, StatutCycleVie.OUVERTURE, StatutCycleVie.EN_COURS)).isEmpty();
    }

    @Test
    void une_condition_remplie_laisse_passer() {
        var conditions = List.of(
                condition(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE, null));

        assertThat(empechements(conditions, StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isEmpty();
    }

    /**
     * Two missing items are reported together. Stopping at the first would make the user fix one thing
     * and discover another on the next click.
     */
    @Test
    void plusieurs_manques_sont_rendus_ensemble() {
        var conditions = List.of(
                condition(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE, "Aucune décision définitive."),
                condition(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE, "Des frais restent impayés."),
                condition(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE, "Sans rapport."));

        assertThat(empechements(conditions, StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE))
                .containsExactly("Aucune décision définitive.", "Des frais restent impayés.");
    }

    @Test
    void sans_aucune_condition_le_comportement_reste_celui_d_avant() {
        assertThat(empechements(List.of(), StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isEmpty();
    }

    /** The reason is shown on screen: a silent refusal would leave the user facing an inert button. */
    @Test
    void un_refus_porte_toujours_un_motif_lisible() {
        var conditions = List.of(
                condition(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE, "Il manque une audience tenue."));

        List<String> motifs = empechements(conditions, StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE);

        assertThat(motifs).hasSize(1);
        assertThat(motifs.get(0)).isNotBlank().doesNotContain("EN_DELIBERE", "null");
    }

    /** The exception remains a 400 `ERR-004`: the client already handles it. */
    @Test
    void le_refus_est_un_ERR_004() {
        ApiException erreur = ApiException.badRequest(
                com.afriland.audiences.common.error.ErrorCodes.TRANSITION_NON_AUTORISEE,
                "Il manque une audience tenue.");

        assertThat(erreur.getCode()).isEqualTo("ERR-004");
        assertThat(erreur.getMessage()).isEqualTo("Il manque une audience tenue.");
    }
}
