package com.afriland.audiences.common.reference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le format des références attribuées par le système. Vérifiable sans base : c'est une mise en
 * forme, et c'est elle que lisent les utilisateurs.
 */
class GenerateurReferenceTest {

    @Test
    void le_format_reprend_celui_des_archives_d_audit() {
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 1)).isEqualTo("DOS-2026-0001");
        assertThat(GenerateurReference.formater(FamilleReference.CLIENT, 2026, 42)).isEqualTo("CLI-2026-0042");
    }

    @Test
    void le_numero_est_complete_a_quatre_chiffres() {
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 7)).isEqualTo("DOS-2026-0007");
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 999)).isEqualTo("DOS-2026-0999");
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 1000)).isEqualTo("DOS-2026-1000");
    }

    /**
     * Au-delà de 9999 dans l'année, le numéro déborde sur cinq chiffres plutôt que de recommencer.
     * Une référence tronquée en désignerait deux.
     */
    @Test
    void au_dela_de_quatre_chiffres_le_numero_deborde_sans_se_tronquer() {
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 10_000)).isEqualTo("DOS-2026-10000");
    }

    @Test
    void chaque_famille_a_son_prefixe_et_aucun_n_est_partage() {
        assertThat(FamilleReference.DOSSIER.prefixe()).isEqualTo("DOS");
        assertThat(FamilleReference.CLIENT.prefixe()).isEqualTo("CLI");

        long distincts = java.util.Arrays.stream(FamilleReference.values())
                .map(FamilleReference::prefixe).distinct().count();
        assertThat(distincts).isEqualTo(FamilleReference.values().length);
    }

    /** La référence doit tenir dans la colonne, qui accepte cinquante caractères. */
    @Test
    void la_reference_tient_dans_la_colonne() {
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 9999)).hasSize(13);
        assertThat(GenerateurReference.formater(FamilleReference.DOSSIER, 2026, 9999).length()).isLessThanOrEqualTo(50);
    }
}
