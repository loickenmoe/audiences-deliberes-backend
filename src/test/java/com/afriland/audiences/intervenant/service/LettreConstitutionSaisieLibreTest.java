package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * What the author can actually type.
 *
 * <p>The PDF is written in Helvetica, and PDFBox <b>throws</b> on any character outside its encoding.
 * Generation runs <b>inside the validation transaction</b> ({@code ConstitutionService#valider}): if it
 * throws, the request stays {@code EN_ATTENTE} and every retry fails the same way, blocking a
 * business process on legitimate input. Free text lives in the <b>body</b>, <b>recipient</b> and
 * <b>subject</b>; none of these inputs is exotic, and all must produce a letter.
 */
class LettreConstitutionSaisieLibreTest {

    private final LettreConstitutionService service = new LettreConstitutionService();

    private static DemandeConstitution demande(String nomPrestataire) {
        return DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom(nomPrestataire).build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif("Besoin local")
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();
    }

    private LettreConstitution lettre(String nomPrestataire) {
        return service.brouillonPour(demande(nomPrestataire), 4);
    }

    /** The most common and most surely fatal case: the Enter key in a text area. */
    @Test
    @DisplayName("un corps sur plusieurs lignes ne bloque pas la validation")
    void corpsMultiligne() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setCorps("Le cabinet habituel est dessaisi.\nUne constitution locale est requise.");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }

    /** Copy-paste from a word processor: narrow no-break space, curly apostrophe, em dash. */
    @Test
    @DisplayName("un corps collé depuis un traitement de texte ne bloque pas la validation")
    void corpsColleDepuisWord() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setCorps("Urgence  absolue  : l’audience est fixée — report impossible…");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }

    /** A provider's name is no more constrained than the body. */
    @Test
    @DisplayName("un nom de prestataire hors Latin-1 ne bloque pas la validation")
    void nomPrestataireHorsLatin1() {
        assertThatCode(() -> service.generer(lettre("Me Nguyễn"))).doesNotThrowAnyException();
    }

    /** A tab in pasted text: invisible on screen, refused by the font. */
    @Test
    @DisplayName("une tabulation dans l'objet ne bloque pas la validation")
    void objetAvecTabulation() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setObjet("Constitution\tet transmission de pièces");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }
}
