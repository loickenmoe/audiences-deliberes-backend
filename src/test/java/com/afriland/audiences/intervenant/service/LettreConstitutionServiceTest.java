package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.pdf.LecturePdf;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La lettre de constitution sur le papier à en-tête de la banque (Q-115 B1).
 *
 * <p>On ne vérifie pas qu'elle est belle — un test ne sait pas cela. On vérifie ce qui se perd sans
 * qu'on s'en aperçoive : la marque, le numéro, le destinataire, l'objet, le signataire, et surtout
 * les <b>pointillés</b> là où rien n'est renseigné. Ce gabarit est fait pour être complété ; une
 * lettre qui rendrait du vide à la place retirerait une possibilité qui existe aujourd'hui.
 */
class LettreConstitutionServiceTest {

    private final LettreConstitutionService service = new LettreConstitutionService();

    private static DemandeConstitution demande() {
        return DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom("Me Fotso").build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif("Besoin local")
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();
    }

    private LettreConstitution lettre() {
        return service.brouillonPour(demande(), 4);
    }

    @Test
    void generer_producesValidPdf() {
        byte[] pdf = service.generer(lettre());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("la lettre porte son numéro, son destinataire, son objet et son signataire")
    void generer_contientLesMentionsDeLaLettre() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre()));

        assertThat(texte).contains("N° 4 /First Bank/DAJR/DR/CM/ABN/18");
        assertThat(texte).contains("Me Fotso").contains("Huissier de Justice");
        assertThat(texte).contains("DOSSIER DOS-2026-001");
        assertThat(texte).contains("Lettre de constitution contenant transmission de pièces.");
        assertThat(texte).contains("La Direction Juridique");
    }

    /**
     * Le papier à en-tête : logo et mentions légales. Ces mentions ne figurent que sur la
     * correspondance — les porter sur un export de calendrier ferait passer un tableau de travail
     * pour une communication officielle de la banque.
     */
    @Test
    @DisplayName("la colonne latérale porte les mentions institutionnelles")
    void generer_porteLaColonneLaterale() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre()));

        assertThat(texte).contains("RC N° : 87R041");
        assertThat(texte).contains("00005/MINFI/DCE2");
        assertThat(texte).contains("CCEI CMCX");
        assertThat(texte).contains("www.afrilandfirstbank.com");
    }

    /** Le corps type du gabarit, ses pointillés conservés là où le rédacteur doit compléter. */
    @Test
    @DisplayName("le corps reprend le gabarit et garde ses pointillés")
    void generer_gardeLesPointilles() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre()));

        /*
         * Fragments courts : le corps revient à la ligne tout seul, et une assertion qui enjamberait
         * une coupure échouerait sur la mise en page au lieu de vérifier le contenu.
         */
        assertThat(texte).contains("Dans le cadre de notre collaboration");
        assertThat(texte).contains("constituons par les").contains("Ladite saisie");
        assertThat(texte).contains("..........");
    }

    /**
     * L'auteur du fichier est le <b>validateur</b>, pas le juriste : c'est la seconde signature qui
     * engage la banque (RG-INT-08).
     */
    @Test
    @DisplayName("le fichier porte son titre et le nom du validateur")
    void generer_porteSesProprietes() throws Exception {
        var proprietes = LecturePdf.proprietes(service.generer(lettre()));

        assertThat(proprietes.getTitle()).isEqualTo("Lettre de constitution N° 4 /First Bank/DAJR/DR/CM/ABN/18");
        assertThat(proprietes.getAuthor()).isEqualTo("Hierarchique Sup");
    }

    /**
     * Un champ vidé par le rédacteur rend des pointillés, jamais « null » ni un blanc : la lettre
     * reste complétable à la main, comme le papier qu'elle remplace.
     */
    @Test
    @DisplayName("un champ vide rend des pointillés, jamais du vide")
    void generer_champVideRendDesPointilles() throws Exception {
        LettreConstitution sansDestinataire = lettre();
        sansDestinataire.setDestinataireNom("");
        sansDestinataire.setConcerne(null);

        String texte = LecturePdf.texte(service.generer(sansDestinataire));

        assertThat(texte).doesNotContain("null");
        assertThat(texte).contains("Maître ..........");
    }
}
