package com.afriland.audiences.common.audit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariants of the history action catalogue.
 *
 * <p>A code without a label, or two codes sharing the same label, breaks no build and only shows
 * up on screen, when an audit filter returns something other than what was requested.
 */
class TypeActionHistoriqueTest {

    @Test
    void chaqueCodePorteUnLibelleNonVide() {
        for (TypeActionHistorique code : TypeActionHistorique.values()) {
            assertThat(code.libelle()).as("libellé de %s", code).isNotBlank();
        }
    }

    /**
     * Two codes with the same label would make the filter confusing: the user would pick one entry and
     * see another one filled.
     */
    @Test
    void aucunLibelleNEstPartageParDeuxCodes() {
        var doublons = Arrays.stream(TypeActionHistorique.values())
                .collect(Collectors.groupingBy(TypeActionHistorique::libelle, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(java.util.Map.Entry::getKey)
                .toList();

        assertThat(doublons).as("libellés partagés").isEmpty();
    }

    /**
     * Constraint {@code chk_historique_code} enumerates the codes explicitly. A code added to the enum
     * without a migration would compile but <b>fail on write</b> in production. This test is the
     * reminder.
     */
    @Test
    void leNombreDeCodesCorrespondAuxSitesInstrumentes() {
        // Every new code requires a migration extending `chk_historique_code`; update this count with it.
        assertThat(TypeActionHistorique.values()).hasSize(59);
        assertThat(TypeActionHistorique.NON_CLASSEE.libelle()).isEqualTo("Action non classée");
    }
}
