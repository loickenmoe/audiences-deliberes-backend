package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.service.RedacteurNotification.ContenuNotification;
import com.afriland.audiences.notification.service.RedacteurNotification.Contexte;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.util.HtmlUtils;

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
 * Étape 5 : textes des notifications externes. Les garde-fous de D18 sont vérifiés sur <b>toutes</b>
 * les combinaisons d'événement et de canal, pas sur un échantillon : un texte ajouté demain sans
 * respecter la règle ferait échouer ce test.
 */
class RedacteurNotificationTest {

    private static final String REFERENCE = "DOS-2026-014";
    private static final String LIEN = "http://localhost:3000/dossiers/5";

    private static final Route OUTLOOK = new Route("OUTLOOK", CanalEngine.EMAIL, "juriste.test@afrilandfirstbank.com", null);
    private static final Route TEAMS = new Route("TEAMS", CanalEngine.TEAMS, "juriste.test@afrilandfirstbank.com", null);
    private static final Route COURRIEL = new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "prestataire.test@example.cm", null);
    private static final Route SMS = new Route("SMS", CanalEngine.SMS, "237600000000", null);
    private static final Route WHATSAPP = new Route("WHATSAPP", CanalEngine.WHATSAPP, "237600000000", null);

    /** Marqueurs, codes de règle ou de décision, montants : rien de tout cela ne doit sortir. */
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
                // La référence et le lien sont les deux seules données insérées ; on les retire avant
                // de vérifier que le gabarit, lui, ne contient rien d'interdit.
                // Le HTML est décodé d'abord : l'apostrophe échappée (&#39;) n'est pas un chiffre du texte.
                String gabarit = HtmlUtils.htmlUnescape(texte).replace(REFERENCE, "").replace(LIEN, "");
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
        Set<String> presentes = new HashSet<>(textes.stringPropertyNames());
        presentes.removeIf(cle -> cle.startsWith("commun."));

        assertThat(presentes).containsExactlyInAnyOrderElementsOf(attendues);
    }

    @Test
    void outlook_complet_en_HTML_avec_reference_et_lien() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.RAPPEL_AUDIENCE, OUTLOOK, new Contexte(REFERENCE, LIEN));

        assertThat(contenu.sujet()).isEqualTo("[Audiences et délibérés] Rappel d'audience - DOS-2026-014");
        assertThat(contenu.corps())
                .startsWith("<p>Une audience approche sur l&#39;un de vos dossiers.</p>")
                .contains("<p>Dossier : DOS-2026-014</p>")
                .contains("<p>Consulter dans l&#39;application : " + LIEN + "</p>")
                .contains("merci de ne pas y répondre");
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
        assertThat(contenu.corps()).startsWith("<p>Bonjour,</p>")
                .contains("Retrouvez le détail dans votre espace : http://localhost:3000/portail")
                .contains("Direction Juridique, Afriland First Bank");
    }

    @Test
    void courriel_personnel_sans_compte_invite_a_contacter_la_direction_juridique() {
        ContenuNotification contenu = redacteur.pourPrestataire(EvenementPrestataire.CORRESPONDANCE_TRANSMISE, COURRIEL,
                new Contexte(REFERENCE, null));

        assertThat(contenu.corps()).contains("contactez la Direction Juridique").doesNotContain("http");
    }

    /** D18 : ni référence ni lien par SMS ou WhatsApp, et un seul segment de 160 caractères au plus. */
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

    @Test
    void une_reference_malveillante_est_echappee_dans_le_HTML() {
        ContenuNotification contenu = redacteur.pourUtilisateur(TypeAlerte.ALARME, OUTLOOK,
                new Contexte("<script>x</script>", null));

        assertThat(contenu.corps()).doesNotContain("<script>").contains("&lt;script&gt;");
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
