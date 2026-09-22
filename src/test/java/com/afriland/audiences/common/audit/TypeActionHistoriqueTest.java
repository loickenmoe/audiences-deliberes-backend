package com.afriland.audiences.common.audit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Q-111 — les invariants du référentiel d'actions.
 *
 * <p>Ce que ces tests protègent est peu spectaculaire et coûteux à perdre : un code sans libellé, ou
 * deux codes partageant la même phrase, ne casse aucune compilation et ne se voit qu'à l'écran, le
 * jour où un filtre d'audit renvoie autre chose que ce qu'on croyait demander.
 */
class TypeActionHistoriqueTest {

    @Test
    void chaqueCodePorteUnLibelleNonVide() {
        for (TypeActionHistorique code : TypeActionHistorique.values()) {
            assertThat(code.libelle()).as("libellé de %s", code).isNotBlank();
        }
    }

    /**
     * Deux codes au même libellé rendraient le filtre incompréhensible : l'utilisateur choisirait
     * une entrée dans une liste et en verrait une autre se remplir.
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
     * La contrainte `chk_historique_code` de V18 énumère les codes en dur. Un code ajouté à
     * l'énumération sans migration passerait la compilation et **échouerait à l'écriture**, en
     * production, sur l'action qu'on venait d'instrumenter. Ce test rappelle le lien.
     */
    @Test
    void leNombreDeCodesCorrespondAuxSitesInstrumentes() {
        // 42 points d'historisation recensés en Q-111, plus AUDIT_CONSULTE (Q-112),
        // AUDIT_EXPORTE et ARCHIVE_TELECHARGEE (Q-113), LETTRE_CONSTITUTION_REDIGEE (Q-115 B2),
        // CLIENT_MODIFIE (PUT /clients),
        // et NON_CLASSEE pour la reprise.
        assertThat(TypeActionHistorique.values()).hasSize(48);
        assertThat(TypeActionHistorique.NON_CLASSEE.libelle()).isEqualTo("Action non classée");
    }
}
