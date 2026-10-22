package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Writes the text of an external notification for a given channel.
 * <p>
 * <b>The in-app alert text is never reused</b>: it may contain a rejection reason, free-text alarm
 * subject, technical marker or rule code. External text comes from a versioned template
 * ({@code notifications/textes_fr.properties}) and only receives the case-file reference and an
 * application link.
 * <p>
 * Three levels of detail:
 * <ul>
 *   <li><b>full</b> (Outlook, Teams): sentence, case-file reference, link;</li>
 *   <li><b>moderate</b> (personal e-mail): greeting, sentence, case-file reference, then the lawyer
 *       portal link if any, otherwise an invitation to contact the Legal Department;</li>
 *   <li><b>minimal</b> (SMS, WhatsApp): event name only, no reference or link, since an SMS can be
 *       read by a third party on a locked screen.</li>
 * </ul>
 * Every channel receives plain text, paragraphs separated by a blank line: the engine delivers
 * {@code directBody} as is, and an HTML body showed its tags to the recipient.
 */
@Component
public class RedacteurNotification {

    static final String RESSOURCE = "notifications/textes_fr.properties";

    private final Properties textes;

    public RedacteurNotification() {
        this.textes = charger();
    }

    /** Text of an alert for an internal user (Outlook or Teams). */
    public ContenuNotification pourUtilisateur(TypeAlerte type, Route route, Contexte contexte) {
        return rediger("interne." + type.name(), route, contexte);
    }

    /** Text of an event for a provider (personal e-mail, SMS or WhatsApp). */
    public ContenuNotification pourPrestataire(EvenementPrestataire evenement, Route route, Contexte contexte) {
        return rediger("prestataire." + evenement.name(), route, contexte);
    }

    private ContenuNotification rediger(String evenement, Route route, Contexte contexte) {
        String titre = texte(evenement + ".titre");
        String phrase = texte(evenement + ".phrase");
        return switch (NiveauDetail.pour(route.reglage())) {
            case COMPLET -> complet(titre, phrase, contexte);
            case MODERE -> modere(titre, phrase, contexte);
            case MINIMAL -> new ContenuNotification(null, minimal(evenement, titre));
        };
    }

    /**
     * Short message of an event: the common header, then the event's own sentence. Each event has its
     * own sentence because the Legal Department writes them individually; without one, the default
     * (event name and where to look) is used. The minimal level never includes a reference or link.
     */
    private String minimal(String evenement, String titre) {
        String propre = textes.getProperty(evenement + ".minimal");
        String corps = propre == null || propre.isBlank()
                ? texte("commun.minimal.defaut").replace("{0}", titre)
                : propre;
        return texte("commun.minimal").replace("{0}", corps);
    }

    private ContenuNotification complet(String titre, String phrase, Contexte contexte) {
        List<String> lignes = new ArrayList<>();
        lignes.add(phrase);
        if (contexte.referenceDossier() != null) {
            lignes.add(texte("commun.dossier").replace("{0}", contexte.referenceDossier()));
        }
        if (contexte.lien() != null) {
            lignes.add(texte("commun.consulter").replace("{0}", contexte.lien()));
        }
        lignes.add(texte("commun.pied"));
        return new ContenuNotification(sujet(titre, contexte), texteBrut(lignes));
    }

    private ContenuNotification modere(String titre, String phrase, Contexte contexte) {
        List<String> lignes = new ArrayList<>();
        lignes.add(texte("commun.salutation"));
        lignes.add(phrase);
        if (contexte.referenceDossier() != null) {
            lignes.add(texte("commun.dossier").replace("{0}", contexte.referenceDossier()));
        }
        lignes.add(contexte.lien() != null
                ? texte("commun.espace").replace("{0}", contexte.lien())
                : texte("commun.contact"));
        lignes.add(texte("commun.emetteur"));
        lignes.add(texte("commun.pied"));
        return new ContenuNotification(sujet(titre, contexte), texteBrut(lignes));
    }

    private String sujet(String titre, Contexte contexte) {
        String sujet = "[" + texte("commun.application") + "] " + titre;
        return contexte.referenceDossier() == null ? sujet : sujet + " - " + contexte.referenceDossier();
    }

    private static String texteBrut(List<String> lignes) {
        return String.join("\n\n", lignes);
    }

    String texte(String cle) {
        String valeur = textes.getProperty(cle);
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalStateException("Texte de notification manquant : " + cle + " (" + RESSOURCE + ")");
        }
        return valeur;
    }

    private static Properties charger() {
        Properties proprietes = new Properties();
        try (InputStream flux = RedacteurNotification.class.getClassLoader().getResourceAsStream(RESSOURCE)) {
            if (flux == null) {
                throw new IllegalStateException("Ressource introuvable : " + RESSOURCE);
            }
            proprietes.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return proprietes;
    }

    /** Level of detail of a configured channel. */
    enum NiveauDetail {
        COMPLET, MODERE, MINIMAL;

        static NiveauDetail pour(String reglage) {
            return switch (reglage) {
                case "OUTLOOK", "TEAMS" -> COMPLET;
                case "COURRIEL_PERSONNEL" -> MODERE;
                case "SMS", "WHATSAPP" -> MINIMAL;
                default -> throw new IllegalArgumentException("Canal réglé inconnu : " + reglage);
            };
        }
    }

    /**
     * The only data an external text may receive.
     *
     * @param referenceDossier case-file reference, or {@code null} (alert without case file)
     * @param lien             application URL to open, or {@code null} (provider without account)
     */
    public record Contexte(String referenceDossier, String lien) {
    }

    /**
     * @param sujet message subject; {@code null} for SMS and WhatsApp
     * @param corps text ready for the engine ({@code directBody})
     */
    public record ContenuNotification(String sujet, String corps) {
    }
}
