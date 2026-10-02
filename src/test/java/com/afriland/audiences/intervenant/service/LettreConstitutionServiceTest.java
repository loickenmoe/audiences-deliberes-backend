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

    /**
     * Le tirage destiné au <b>papier préimprimé</b> de la banque (observation métier du 2026-09-25).
     *
     * <p>Ce que ces trois tests protègent : le décor disparaît, le <b>contenu</b> reste entier, et
     * surtout la <b>géométrie ne bouge pas</b>. C'est ce dernier point qui compte à l'impression —
     * un texte remonté de trois centimètres se poserait sur le logo de la feuille.
     */
    @Test
    @DisplayName("le tirage pour papier préimprimé ne porte ni logo ni mentions légales")
    void preimprime_neDessinePasLeGabarit() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre(), true));

        assertThat(texte).doesNotContain("RC N° : 87R041");
        assertThat(texte).doesNotContain("00005/MINFI/DCE2");
        assertThat(texte).doesNotContain("CCEI CMCX");
        assertThat(texte).doesNotContain("www.afrilandfirstbank.com");
    }

    @Test
    @DisplayName("le tirage pour papier préimprimé garde tout le contenu de la lettre")
    void preimprime_gardeLeContenu() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre(), true));

        assertThat(texte).contains("N° 4 /First Bank/DAJR/DR/CM/ABN/18");
        assertThat(texte).contains("Me Fotso").contains("Huissier de Justice");
        assertThat(texte).contains("DOSSIER DOS-2026-001");
        assertThat(texte).contains("Lettre de constitution contenant transmission de pièces.");
        assertThat(texte).contains("La Direction Juridique");
        assertThat(texte).contains("Dans le cadre de notre collaboration");
    }

    /**
     * La preuve que le contenu ne s'est pas déplacé : les mêmes mots, dans le même ordre, aux mêmes
     * coordonnées. Comparer les positions plutôt que l'allure générale est le seul moyen de vérifier
     * qu'une feuille préimprimée recevra le texte au bon endroit.
     */
    @Test
    @DisplayName("le contenu occupe exactement la même place que sur le papier à en-tête")
    void preimprime_conserveLaGeometrieDuContenu() throws Exception {
        String avecGabarit = LecturePdf.positions(service.generer(lettre(), false));
        String sansGabarit = LecturePdf.positions(service.generer(lettre(), true));

        // Les mentions de la colonne n'existent que sur le premier : on compare ce qui reste.
        for (String ligne : sansGabarit.lines().toList()) {
            assertThat(avecGabarit).contains(ligne);
        }
    }

    /**
     * Non-régression : l'exemplaire archivé en GED — celui qui fait foi — n'a pas changé d'un octet
     * de mise en page. Le tirage préimprimé s'ajoute, il ne remplace rien.
     */
    @Test
    @DisplayName("l'exemplaire archivé reste celui du papier à en-tête")
    void generer_parDefautResteLePapierEnTete() throws Exception {
        assertThat(LecturePdf.positions(service.generer(lettre())))
                .isEqualTo(LecturePdf.positions(service.generer(lettre(), false)));
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
