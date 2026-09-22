package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Rédige le texte d'une notification externe pour un canal donné (NOTIFICATION_ENGINE_PLAN.md, D3 et D18).
 * <p>
 * <b>Le texte de l'alerte applicative n'est jamais réutilisé.</b> Il est écrit pour l'écran : il peut
 * contenir un motif de rejet, l'objet libre d'une alarme, un marqueur technique ou un code de règle.
 * Le texte externe part d'un gabarit versionné ({@code notifications/textes_fr.properties}) et ne
 * reçoit que deux données : la référence du dossier et un lien vers l'application.
 * <p>
 * Trois niveaux de détail (D18) :
 * <ul>
 *   <li><b>complet</b> (Outlook, Teams) : phrase, référence du dossier, lien ;</li>
 *   <li><b>modéré</b> (courriel personnel) : salutation, phrase, référence du dossier, puis le lien de
 *       l'espace avocat s'il existe, sinon une invitation à contacter la Direction Juridique ;</li>
 *   <li><b>minimal</b> (SMS, WhatsApp) : le seul nom de l'événement, sans référence ni lien. Un SMS
 *       peut être lu par un tiers sur un écran verrouillé.</li>
 * </ul>
 * Les courriels partent en HTML (le moteur rend {@code directBody} tel quel), les autres canaux en
 * texte brut. Toute donnée insérée dans le HTML est échappée.
 */
@Component
public class RedacteurNotification {

    static final String RESSOURCE = "notifications/textes_fr.properties";

    private final Properties textes;

    public RedacteurNotification() {
        this.textes = charger();
    }

    /** Texte d'une alerte destinée à un utilisateur interne (Outlook ou Teams). */
    public ContenuNotification pourUtilisateur(TypeAlerte type, Route route, Contexte contexte) {
        return rediger("interne." + type.name(), route, contexte);
    }

    /** Texte d'un événement destiné à un prestataire (courriel personnel, SMS ou WhatsApp). */
    public ContenuNotification pourPrestataire(EvenementPrestataire evenement, Route route, Contexte contexte) {
        return rediger("prestataire." + evenement.name(), route, contexte);
    }

    private ContenuNotification rediger(String evenement, Route route, Contexte contexte) {
        String titre = texte(evenement + ".titre");
        String phrase = texte(evenement + ".phrase");
        return switch (NiveauDetail.pour(route.reglage())) {
            case COMPLET -> complet(titre, phrase, route.canal(), contexte);
            case MODERE -> modere(titre, phrase, contexte);
            case MINIMAL -> new ContenuNotification(null, texte("commun.minimal").replace("{0}", titre));
        };
    }

    private ContenuNotification complet(String titre, String phrase, CanalEngine canal, Contexte contexte) {
        List<String> lignes = new ArrayList<>();
        lignes.add(phrase);
        if (contexte.referenceDossier() != null) {
            lignes.add(texte("commun.dossier").replace("{0}", contexte.referenceDossier()));
        }
        if (contexte.lien() != null) {
            lignes.add(texte("commun.consulter").replace("{0}", contexte.lien()));
        }
        lignes.add(texte("commun.pied"));
        return new ContenuNotification(sujet(titre, contexte),
                canal == CanalEngine.EMAIL ? html(lignes) : String.join("\n\n", lignes));
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
        return new ContenuNotification(sujet(titre, contexte), html(lignes));
    }

    private String sujet(String titre, Contexte contexte) {
        String sujet = "[" + texte("commun.application") + "] " + titre;
        return contexte.referenceDossier() == null ? sujet : sujet + " - " + contexte.referenceDossier();
    }

    private static String html(List<String> lignes) {
        StringBuilder corps = new StringBuilder();
        for (String ligne : lignes) {
            corps.append("<p>").append(HtmlUtils.htmlEscape(ligne, StandardCharsets.UTF_8.name())).append("</p>");
        }
        return corps.toString();
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

    /** Niveau de détail d'un canal réglé (D18). */
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
     * Les seules données qu'un texte externe peut recevoir.
     *
     * @param referenceDossier référence du dossier concerné, ou {@code null} (alerte hors dossier)
     * @param lien             adresse de l'application à ouvrir, ou {@code null} (prestataire sans compte)
     */
    public record Contexte(String referenceDossier, String lien) {
    }

    /**
     * @param sujet objet du message ; {@code null} pour SMS et WhatsApp, qui n'en ont pas
     * @param corps texte prêt à transmettre au moteur ({@code directBody})
     */
    public record ContenuNotification(String sujet, String corps) {
    }
}
