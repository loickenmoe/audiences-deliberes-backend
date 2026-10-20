package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.service.RedacteurNotification.ContenuNotification;
import com.afriland.audiences.notification.service.RedacteurNotification.Contexte;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 5: external notification texts. The D18 safeguards are checked on <b>every</b> event/channel
 * combination, not a sample: a new text breaking the rule would fail this test.
 */
class RedacteurNotificationTest {

    private static final String REFERENCE = "DOS-2026-014";
    private static final String LIEN = "http://localhost:3000/dossiers/5";

    private static final Route OUTLOOK = new Route("OUTLOOK", CanalEngine.EMAIL, "juriste.test@afrilandfirstbank.com", null);
    private static final Route TEAMS = new Route("TEAMS", CanalEngine.TEAMS, "juriste.test@afrilandfirstbank.com", null);
    private static final Route COURRIEL = new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "prestataire.test@example.cm", null);
    private static final Route SMS = new Route("SMS", CanalEngine.SMS, "+237600000000", null);
    private static final Route WHATSAPP = new Route("WHATSAPP", CanalEngine.WHATSAPP, "+237600000000", null);

    /** Markers, rule or decision codes, amounts: none of these may leave. */
    private static final List<Pattern> INTERDITS = List.of(
            Pattern.compile("\\[[A-Za-z]+Id:"),
            Pattern.compile("\\b(RG|FR|NF|US|UC|CONF)-[A-Z]*-?\\d"),
            Pattern.compile("\\bQ-\\d"),
            Pattern.compile("(?i)\\b(FCFA|XAF)\\b"),
            Pattern.compile("\\d"));

    private final RedacteurNotification redacteur = new RedacteurNotification();

    private static Stream<ContenuNotification> tousLesContenus(RedacteurNotification redacteur, Contexte contexte) {
        Stream<ContenuNotification> internes = Arrays.stream(TypeAlerte.values())
                .flatMap(type -> Stream.of(OUTLOOK, TEAMS).map(route -> redacteur.pourUtilisateur(type, route, contexte)));
        Stream<ContenuNotification> prestataires = Arrays.stream(EvenementPrestataire.values())
                .flatMap(ev -> Stream.of(COURRIEL, SMS, WHATSAPP).map(route -> redacteur.pourPrestataire(ev, route, contexte)));
        return Stream.concat(internes, prestataires);
    }

    @Test
    void aucun_texte_ne_contient_marqueur_code_interne_montant_ni_chiffre_hors_reference() {
        tousLesContenus(redacteur, new Contexte(REFERENCE, LIEN)).forEach(contenu -> {
            for (String texte : new String[] {contenu.sujet(), contenu.corps()}) {
                if (texte == null) {
                    continue;
                }
                // The reference and link are the only inserted data; they are removed before checking the
                // template contains nothing forbidden.
                String gabarit = texte.replace(REFERENCE, "").replace(LIEN, "");
                for (Pattern interdit : INTERDITS) {
                    assertThat(interdit.matcher(gabarit).find()).as(interdit + " dans : " + texte).isFalse();
                }
            }
        });
    }

    @Test
    void chaque_evenement_a_un_titre_et_une_phrase_et_aucun_texte_n_est_orphelin() throws Exception {
        Properties textes = new Properties();
        try (var flux = getClass().getClassLoader().getResourceAsStream(RedacteurNotification.RESSOURCE)) {
            textes.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        }
        Set<String> attendues = new HashSet<>();
        for (TypeAlerte type : TypeAlerte.values()) {
            attendues.add("interne." + type.name() + ".titre");
            attendues.add("interne." + type.name() + ".phrase");
        }
        for (EvenementPrestataire evenement : EvenementPrestataire.values()) {
            attendues.add("prestataire." + evenement.name() + ".titre");
            attendues.add("prestataire." + evenement.name() + ".phrase");
        }
        /*
         * The short sentence (SMS, WhatsApp) is **optional**: without it, the event falls back to the
         * common default. It must however name a **known** event: a short sentence set on a nonexistent name
         * would never be read.
         */
        Set<String> courtesAdmises = new HashSet<>();
        for (TypeAlerte type : TypeAlerte.values()) {
            courtesAdmises.add("interne." + type.name() + ".minimal");
        }
        for (EvenementPrestataire evenement : EvenementPrestataire.values()) {
            courtesAdmises.add("prestataire." + evenement.name() + ".minimal");
        }

        Set<String> presentes = new HashSet<>(textes.stringPropertyNames());
        presentes.removeIf(cle -> cle.startsWith("commun."));
        Set<String> courtes = new HashSet<>(presentes);
        courtes.removeIf(cle -> !cle.endsWith(".minimal"));
        assertThat(courtesAdmises).as("phrase courte sur un événement inconnu").containsAll(courtes);
        presentes.removeAll(courtes);

        assertThat(presentes).containsExactlyInAnyOrderElementsOf(attendues);
    }

    @Test
    void outlook_complet_en_texte_brut_avec_reference_et_lien() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.RAPPEL_AUDIENCE, OUTLOOK, new Contexte(REFERENCE, LIEN));

        assertThat(contenu.sujet()).isEqualTo("[Audiences et délibérés] Rappel d'audience - DOS-2026-014");
        assertThat(contenu.corps())
                .doesNotContain("<p>", "</p>", "&#39;")
                .isEqualTo("Une audience approche sur l'un de vos dossiers.\n\nDossier : DOS-2026-014\n\n"
                        + "Consulter dans l'application : " + LIEN + "\n\nMessage automatique, merci de ne pas y répondre.");
    }

    @Test
    void teams_complet_en_texte_brut() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.DOCUMENT_CHARGE, TEAMS, new Contexte(REFERENCE, LIEN));

        assertThat(contenu.corps()).doesNotContain("<p>")
                .isEqualTo("Un document a été ajouté à l'un de vos dossiers.\n\nDossier : DOS-2026-014\n\n"
                        + "Consulter dans l'application : " + LIEN + "\n\nMessage automatique, merci de ne pas y répondre.");
    }

    @Test
    void alerte_hors_dossier_sans_reference() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.CHARGE_MAX_AVOCAT, OUTLOOK,
                new Contexte(null, "http://localhost:3000/notifications"));

        assertThat(contenu.sujet()).isEqualTo("[Audiences et délibérés] Charge maximale d'un avocat");
        assertThat(contenu.corps()).doesNotContain("Dossier :");
    }

    @Test
    void courriel_personnel_modere_renvoie_a_l_espace_avocat_s_il_existe() {
        ContenuNotification contenu = redacteur.pourPrestataire(EvenementPrestataire.AFFECTATION_DOSSIER, COURRIEL,
                new Contexte(REFERENCE, "http://localhost:3000/portail"));

        assertThat(contenu.sujet()).isEqualTo("[Audiences et délibérés] Nouveau dossier confié - DOS-2026-014");
        assertThat(contenu.corps()).startsWith("Bonjour,\n\n").doesNotContain("<p>")
                .contains("Retrouvez le détail dans votre espace : http://localhost:3000/portail")
                .contains("Direction Juridique, Afriland First Bank");
    }

    @Test
    void courriel_personnel_sans_compte_invite_a_contacter_la_direction_juridique() {
        ContenuNotification contenu = redacteur.pourPrestataire(EvenementPrestataire.CORRESPONDANCE_TRANSMISE, COURRIEL,
                new Contexte(REFERENCE, null));

        assertThat(contenu.corps()).contains("contactez la Direction Juridique").doesNotContain("http");
    }

    /** D18: no reference or link by SMS or WhatsApp, and a single segment of at most 160 characters. */
    @ParameterizedTest
    @EnumSource(EvenementPrestataire.class)
    void sms_et_whatsapp_minimaux(EvenementPrestataire evenement) {
        for (Route route : List.of(SMS, WHATSAPP)) {
            ContenuNotification contenu = redacteur.pourPrestataire(evenement, route, new Contexte(REFERENCE, LIEN));

            assertThat(contenu.sujet()).isNull();
            assertThat(contenu.corps()).doesNotContain(REFERENCE, "http", "<").hasSizeLessThanOrEqualTo(160)
                    .startsWith("Afriland First Bank, Direction Juridique : ");
        }
    }

    /**
     * The retainer text, <b>word for word</b>, as written by the Legal Department. It goes out under the
     * bank's name: a generic shape test would let an accidental rewording through.
     */
    @Test
    void le_message_court_de_la_constitution_est_celui_dicte_par_la_direction_juridique() {
        ContenuNotification contenu = redacteur.pourPrestataire(
                EvenementPrestataire.CONSTITUTION_VALIDEE, SMS, new Contexte(REFERENCE, LIEN));

        assertThat(contenu.corps()).isEqualTo("Afriland First Bank, Direction Juridique : "
                + "Vous avez été notifié(e) d'une lettre de constitution. "
                + "Veuillez consulter votre boîte mail.");
    }

    /**
     * The other events are unchanged: only <b>one</b> message was rewritten, and the others must not
     * change unreviewed under the bank's name.
     */
    @Test
    void les_autres_messages_courts_gardent_leur_formulation() {
        ContenuNotification contenu = redacteur.pourPrestataire(
                EvenementPrestataire.FRAIS_REJETES, SMS, new Contexte(REFERENCE, LIEN));

        assertThat(contenu.corps()).isEqualTo("Afriland First Bank, Direction Juridique : "
                + "Demande de frais rejetée. "
                + "Détails par courriel ou auprès de la Direction Juridique.");
    }

    @Test
    void une_donnee_inseree_reste_du_texte_brut() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.ALARME, OUTLOOK,
                new Contexte("<script>x</script>", null));

        // Plain text is not interpreted by the mail client: the value is shown as typed, never as markup.
        assertThat(contenu.corps()).contains("Dossier : <script>x</script>").doesNotContain("&lt;", "<p>");
    }

    @Test
    void un_canal_regle_inconnu_est_refuse() {
        Route inconnue = new Route("PIGEON", CanalEngine.EMAIL, "a@b.cm", null);
        assertThatThrownBy(() -> redacteur.pourUtilisateur(TypeAlerte.ALARME, inconnue, new Contexte(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void liens_de_l_application() {
        LiensApplication liens = new LiensApplication("http://localhost:3000/");

        assertThat(liens.pourUtilisateur(5L)).isEqualTo("http://localhost:3000/dossiers/5");
        assertThat(liens.pourUtilisateur(null)).isEqualTo("http://localhost:3000/notifications");
        assertThat(liens.portailAvocat()).isEqualTo("http://localhost:3000/portail");
    }
}
