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
 * The retainer letter on the bank's letterhead.
 *
 * <p>Checks what gets lost unnoticed: branding, number, recipient, subject, signatory, and above all
 * the <b>dotted lines</b> where nothing is filled in. The template is meant to be completed by hand.
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

        assertThat(texte).contains("N° 4 /First Bank/DAJR/DR/CM/ABN/18")
                .contains("Me Fotso").contains("Huissier de Justice")
                .contains("DOSSIER DOS-2026-001")
                .contains("Lettre de constitution contenant transmission de pièces.")
                .contains("La Direction Juridique");
    }

    /**
     * Letterhead: logo and legal notices. These appear only on correspondence; putting them on a calendar
     * export would make a working table look like an official bank communication.
     */
    @Test
    @DisplayName("la colonne latérale porte les mentions institutionnelles")
    void generer_porteLaColonneLaterale() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre()));

        assertThat(texte).contains("RC N° : 87R041")
                .contains("00005/MINFI/DCE2")
                .contains("CCEI CMCX")
                .contains("www.afrilandfirstbank.com");
    }

    /**
     * The copy for the bank's <b>pre-printed paper</b>. Protects three things: the decoration disappears,
     * the <b>content</b> stays complete, and the <b>geometry does not move</b> (shifted text would land on
     * the sheet's logo).
     */
    @Test
    @DisplayName("le tirage pour papier préimprimé ne porte ni logo ni mentions légales")
    void preimprime_neDessinePasLeGabarit() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre(), true));

        assertThat(texte).doesNotContain("RC N° : 87R041")
                .doesNotContain("00005/MINFI/DCE2")
                .doesNotContain("CCEI CMCX")
                .doesNotContain("www.afrilandfirstbank.com");
    }

    @Test
    @DisplayName("le tirage pour papier préimprimé garde tout le contenu de la lettre")
    void preimprime_gardeLeContenu() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre(), true));

        assertThat(texte).contains("N° 4 /First Bank/DAJR/DR/CM/ABN/18")
                .contains("Me Fotso").contains("Huissier de Justice")
                .contains("DOSSIER DOS-2026-001")
                .contains("Lettre de constitution contenant transmission de pièces.")
                .contains("La Direction Juridique")
                .contains("Dans le cadre de notre collaboration");
    }

    /**
     * Proof the content did not move: same words, same order, same coordinates. Comparing positions is
     * the only way to check a pre-printed sheet receives the text in the right place.
     */
    @Test
    @DisplayName("le contenu occupe exactement la même place que sur le papier à en-tête")
    void preimprime_conserveLaGeometrieDuContenu() throws Exception {
        String avecGabarit = LecturePdf.positions(service.generer(lettre(), false));
        String sansGabarit = LecturePdf.positions(service.generer(lettre(), true));

        // The column notices only exist on the first one: compare what remains.
        for (String ligne : sansGabarit.lines().toList()) {
            assertThat(avecGabarit).contains(ligne);
        }
    }

    /**
     * Non-regression: the copy archived in the GED (the authoritative one) keeps exactly the same
     * layout. The pre-printed copy is added, it replaces nothing.
     */
    @Test
    @DisplayName("l'exemplaire archivé reste celui du papier à en-tête")
    void generer_parDefautResteLePapierEnTete() throws Exception {
        assertThat(LecturePdf.positions(service.generer(lettre())))
                .isEqualTo(LecturePdf.positions(service.generer(lettre(), false)));
    }

    /** The template's default body, with dotted lines kept where the author must complete it. */
    @Test
    @DisplayName("le corps reprend le gabarit et garde ses pointillés")
    void generer_gardeLesPointilles() throws Exception {
        String texte = LecturePdf.texte(service.generer(lettre()));

        /*
         * Short fragments: the body wraps on its own, and an assertion spanning a line break would fail on
         * layout instead of checking content.
         */
        assertThat(texte).contains("Dans le cadre de notre collaboration")
                .contains("constituons par les").contains("Ladite saisie")
                .contains("..........");
    }

    /**
     * The file author is the <b>validator</b>, not the legal officer: the second signature commits the
     * bank (RG-INT-08).
     */
    @Test
    @DisplayName("le fichier porte son titre et le nom du validateur")
    void generer_porteSesProprietes() throws Exception {
        var proprietes = LecturePdf.proprietes(service.generer(lettre()));

        assertThat(proprietes.getTitle()).isEqualTo("Lettre de constitution N° 4 /First Bank/DAJR/DR/CM/ABN/18");
        assertThat(proprietes.getAuthor()).isEqualTo("Hierarchique Sup");
    }

    /**
     * A field cleared by the author renders dotted lines, never "null" or a blank: the letter stays
     * completable by hand, like the paper it replaces.
     */
    @Test
    @DisplayName("un champ vide rend des pointillés, jamais du vide")
    void generer_champVideRendDesPointilles() throws Exception {
        LettreConstitution sansDestinataire = lettre();
        sansDestinataire.setDestinataireNom("");
        sansDestinataire.setConcerne(null);

        String texte = LecturePdf.texte(service.generer(sansDestinataire));

        assertThat(texte).doesNotContain("null")
                .contains("Maître ..........");
    }
}
