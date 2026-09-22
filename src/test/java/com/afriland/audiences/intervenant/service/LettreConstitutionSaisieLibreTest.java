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
 * Ce que le rédacteur peut réellement taper (A1, étendu en B1).
 *
 * <p><b>Pourquoi ces cas.</b> Le PDF est écrit en Helvetica, et PDFBox <b>lève</b> dès qu'on lui
 * demande un caractère absent de son encodage. Or la génération a lieu <b>dans la transaction de
 * validation</b> ({@code ConstitutionService#valider}) : si elle lève, la demande reste
 * {@code EN_ATTENTE}, le SH reçoit une erreur technique, le prestataire n'est pas notifié — et comme
 * le contenu est enregistré, chaque nouvelle tentative échoue à l'identique. Ce n'est pas un export
 * raté, c'est un processus métier bloqué par une saisie légitime.
 *
 * <p><b>Ce qui a changé en B1.</b> Le motif ne figure plus dans la lettre : la saisie libre s'est
 * déplacée vers le <b>corps</b>, le <b>destinataire</b> et l'<b>objet</b> — qui deviendront tous
 * éditables au jalon suivant. Le risque n'a donc pas disparu, il a changé de champ, et ces cas le
 * suivent.
 *
 * <p>Aucune de ces saisies n'est exotique : un retour à la ligne, un texte collé depuis un traitement
 * de texte, un nom étranger. Toutes doivent produire une lettre.
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

    /** Le cas le plus banal, et le plus sûrement fatal : la touche Entrée dans une zone de texte. */
    @Test
    @DisplayName("un corps sur plusieurs lignes ne bloque pas la validation")
    void corpsMultiligne() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setCorps("Le cabinet habituel est dessaisi.\nUne constitution locale est requise.");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }

    /** Copier-coller depuis un traitement de texte : espace fine insécable, apostrophe courbe, cadratin. */
    @Test
    @DisplayName("un corps collé depuis un traitement de texte ne bloque pas la validation")
    void corpsColleDepuisWord() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setCorps("Urgence  absolue  : l’audience est fixée — report impossible…");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }

    /** Le nom d'un prestataire n'est pas plus contraint que le corps. */
    @Test
    @DisplayName("un nom de prestataire hors Latin-1 ne bloque pas la validation")
    void nomPrestataireHorsLatin1() {
        assertThatCode(() -> service.generer(lettre("Me Nguyễn"))).doesNotThrowAnyException();
    }

    /** Une tabulation dans un collage : invisible à l'écran, refusée par la police. */
    @Test
    @DisplayName("une tabulation dans l'objet ne bloque pas la validation")
    void objetAvecTabulation() {
        LettreConstitution lettre = lettre("Me Fotso");
        lettre.setObjet("Constitution\tet transmission de pièces");

        assertThatCode(() -> service.generer(lettre)).doesNotThrowAnyException();
    }
}
