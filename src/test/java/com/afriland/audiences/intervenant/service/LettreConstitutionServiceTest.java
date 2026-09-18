package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.common.pdf.LecturePdf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LettreConstitutionServiceTest {

    private final LettreConstitutionService service = new LettreConstitutionService();

    @Test
    void generer_producesValidPdf() {
        DemandeConstitution demande = DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom("Me Fotso").build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif("Besoin local")
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();

        byte[] pdf = service.generer(demande);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    /**
     * Ce que la lettre **dit**, et non plus seulement qu'elle est un PDF (A1).
     *
     * <p>Une lettre versee au dossier et transmise a un prestataire doit porter le dossier, le
     * prestataire, le motif et le validateur. La version precedente de ce test aurait laisse passer
     * une lettre entierement vide.
     */
    @Test
    @DisplayName("la lettre porte le dossier, le prestataire, le motif et son validateur")
    void generer_contientLesMentionsDeLaLettre() throws Exception {
        String texte = LecturePdf.texte(service.generer(demande("Besoin local")));

        assertThat(texte).contains("LETTRE DE CONSTITUTION");
        assertThat(texte).contains("DOS-2026-001");
        assertThat(texte).contains("Me Fotso");
        assertThat(texte).contains("Besoin local");
        assertThat(texte).contains("Hierarchique");
    }

    /**
     * L'auteur du fichier est le **validateur**, pas le juriste : c'est la seconde signature qui
     * engage la banque (RG-INT-08).
     */
    @Test
    @DisplayName("le fichier porte son titre et le nom du validateur")
    void generer_porteSesProprietes() throws Exception {
        var proprietes = LecturePdf.proprietes(service.generer(demande("Besoin local")));

        assertThat(proprietes.getTitle()).isEqualTo("Lettre de constitution - dossier DOS-2026-001");
        assertThat(proprietes.getAuthor()).isEqualTo("Hierarchique Sup");
    }

    private static DemandeConstitution demande(String motif) {
        return DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom("Me Fotso").build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif(motif)
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();
    }
}
