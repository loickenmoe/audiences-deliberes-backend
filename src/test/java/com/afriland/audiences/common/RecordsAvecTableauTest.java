package com.afriland.audiences.common;

import com.afriland.audiences.common.audit.AuditExportService.Extraction;
import com.afriland.audiences.intervenant.service.LettreConstitutionService.Signataire;
import com.afriland.audiences.referentiel.service.SignatureService.SignatureDeposee;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records holding a byte array compare its content, not its reference, and never print the bytes.
 */
class RecordsAvecTableauTest {

    private static final LocalDateTime DEPOT = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Test
    void un_signataire_se_compare_par_le_contenu_de_son_image() {
        Signataire a = new Signataire(new byte[] {1, 2, 3}, "Jean");
        Signataire b = new Signataire(new byte[] {1, 2, 3}, "Jean");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b)
                .isNotEqualTo(new Signataire(new byte[] {9}, "Jean"))
                .isNotEqualTo(new Signataire(new byte[] {1, 2, 3}, "Paul"));
        assertThat(a.toString()).contains("3 octets", "Jean");
        assertThat(new Signataire(null, "Jean").toString()).contains("image=null");
    }

    @Test
    void une_signature_deposee_se_compare_par_le_contenu_de_son_image() {
        SignatureDeposee a = new SignatureDeposee(new byte[] {4, 5}, "image/png", DEPOT);
        SignatureDeposee b = new SignatureDeposee(new byte[] {4, 5}, "image/png", DEPOT);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b)
                .isNotEqualTo(new SignatureDeposee(new byte[] {4}, "image/png", DEPOT))
                .isNotEqualTo(new SignatureDeposee(new byte[] {4, 5}, "image/jpeg", DEPOT))
                .isNotEqualTo(new SignatureDeposee(new byte[] {4, 5}, "image/png", DEPOT.plusDays(1)));
        assertThat(a.toString()).contains("2 octets", "image/png").doesNotContain("[4, 5]");
        assertThat(new SignatureDeposee(null, null, null).toString()).contains("image=null");
    }

    @Test
    void une_extraction_se_compare_par_le_contenu_du_fichier() {
        Extraction a = new Extraction(new byte[] {7, 7, 7, 7}, null, "audit.pdf");
        Extraction b = new Extraction(new byte[] {7, 7, 7, 7}, null, "audit.pdf");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b)
                .isNotEqualTo(new Extraction(new byte[] {7}, null, "audit.pdf"))
                .isNotEqualTo(new Extraction(new byte[] {7, 7, 7, 7}, null, "autre.pdf"));
        assertThat(a.toString()).contains("4 octets", "audit.pdf");
        assertThat(new Extraction(null, null, "vide.pdf").toString()).contains("contenu=null");
    }
}
