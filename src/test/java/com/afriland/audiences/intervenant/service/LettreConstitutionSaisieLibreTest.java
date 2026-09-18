package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Ce que le juriste peut réellement taper (A1).
 *
 * <p><b>Pourquoi ces cas.</b> Le motif est saisi dans un {@code <Textarea>} et recopié tel quel dans
 * la lettre. Or la génération a lieu <b>dans la transaction de validation</b>
 * ({@code ConstitutionService#valider}) : si elle lève, la demande reste {@code EN_ATTENTE}, le SH
 * reçoit une erreur technique, le prestataire n'est pas notifié — et comme le motif est enregistré,
 * chaque nouvelle tentative échoue à l'identique. Ce n'est pas un export raté, c'est un processus
 * métier bloqué par une saisie légitime.
 *
 * <p>Aucun de ces motifs n'est exotique : un retour à la ligne, un texte collé depuis un traitement
 * de texte, un nom étranger. Ils doivent tous produire une lettre.
 */
class LettreConstitutionSaisieLibreTest {

    private final LettreConstitutionService service = new LettreConstitutionService();

    private static DemandeConstitution demandeAvec(String motif, String nomPrestataire) {
        return DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom(nomPrestataire).build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif(motif)
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();
    }

    /** Le cas le plus banal, et le plus sûrement fatal : la touche Entrée dans une zone de texte. */
    @Test
    @DisplayName("un motif sur plusieurs lignes ne bloque pas la validation")
    void motifMultiligne() {
        assertThatCode(() -> service.generer(demandeAvec(
                "Le cabinet habituel est dessaisi.\nUne constitution locale est requise avant l'audience.",
                "Me Fotso")))
                .doesNotThrowAnyException();
    }

    /** Copier-coller depuis un traitement de texte : espace fine insécable, apostrophe courbe, tiret long. */
    @Test
    @DisplayName("un motif collé depuis un traitement de texte ne bloque pas la validation")
    void motifColleDepuisWord() {
        assertThatCode(() -> service.generer(demandeAvec(
                "Urgence  absolue  : l’audience est fixée — report impossible…",
                "Me Fotso")))
                .doesNotThrowAnyException();
    }

    /** Le nom d'un prestataire n'est pas plus contraint que le motif. */
    @Test
    @DisplayName("un nom de prestataire hors Latin-1 ne bloque pas la validation")
    void nomPrestataireHorsLatin1() {
        assertThatCode(() -> service.generer(demandeAvec("Besoin local", "Me Nguyễn")))
                .doesNotThrowAnyException();
    }

    /** Une tabulation dans un collage : invisible à l'écran, refusée par la police. */
    @Test
    @DisplayName("une tabulation dans le motif ne bloque pas la validation")
    void motifAvecTabulation() {
        assertThatCode(() -> service.generer(demandeAvec("Besoin local\tconstat d'huissier", "Me Fotso")))
                .doesNotThrowAnyException();
    }
}
