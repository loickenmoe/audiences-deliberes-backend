package com.afriland.audiences.notification.domain;

import com.afriland.audiences.notification.passerelle.CanalEngine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Une notification externe en file d'attente : un destinataire, un canal (voir la migration V27).
 * <p>
 * Les lignes naissent par {@link #aRemettre} ou {@link #nonJoignable}, jamais par un constructeur
 * public : l'invariant « destinataire présent, sauf si NON_JOIGNABLE » est ainsi tenu dès Java, et
 * pas seulement par la contrainte en base. Les transitions d'envoi et de suivi arrivent avec le
 * dispatcher (étape 8) et la consommation des accusés (étape 9).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_envoi")
public class NotificationEnvoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 200)
    private String eventId;

    @Column(name = "type_evenement", nullable = false, length = 60)
    private String typeEvenement;

    @Column(name = "dossier_id")
    private Long dossierId;

    @Column(name = "alerte_id")
    private Long alerteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinataire_type", nullable = false, length = 20)
    private TypeDestinataire destinataireType;

    @Column(name = "destinataire_ref")
    private Long destinataireRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalEngine canal;

    @Column(length = 320)
    private String destinataire;

    @Column(name = "destinataire_origine", length = 320)
    private String destinataireOrigine;

    @Column(length = 255)
    private String sujet;

    @Column(columnDefinition = "TEXT")
    private String corps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEnvoi statut;

    @Column(nullable = false)
    private int tentatives;

    @Column(name = "prochaine_tentative")
    private LocalDateTime prochaineTentative;

    @Column(name = "derniere_erreur", length = 1000)
    private String derniereErreur;

    @Column(name = "statut_engine", length = 20)
    private String statutEngine;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_publication")
    private LocalDateTime datePublication;

    @Column(name = "date_accuse")
    private LocalDateTime dateAccuse;

    /** Ligne prête à partir : elle sera remise au transport au prochain passage du dispatcher. */
    public static NotificationEnvoi aRemettre(Origine origine, CanalEngine canal, String destinataire,
                                              String sujet, String corps) {
        if (destinataire == null || destinataire.isBlank()) {
            throw new IllegalArgumentException("Destinataire absent : utiliser nonJoignable");
        }
        if (corps == null || corps.isBlank()) {
            throw new IllegalArgumentException("Le corps du message est obligatoire");
        }
        NotificationEnvoi envoi = nouvelle(origine, canal);
        envoi.destinataire = destinataire;
        envoi.sujet = sujet;
        envoi.corps = corps;
        envoi.statut = StatutEnvoi.EN_ATTENTE;
        envoi.prochaineTentative = LocalDateTime.now();
        return envoi;
    }

    /**
     * Canal routé sans coordonnée utilisable : rien ne part, mais l'écart reste visible en supervision
     * (D14), avec son motif (coordonnée absente ou invalide) dans {@code derniereErreur}.
     */
    public static NotificationEnvoi nonJoignable(Origine origine, CanalEngine canal, String motif) {
        NotificationEnvoi envoi = nouvelle(origine, canal);
        envoi.statut = StatutEnvoi.NON_JOIGNABLE;
        envoi.derniereErreur = motif;
        return envoi;
    }

    /**
     * Le message a été remis au transport. L'envoi effectif n'est pas encore confirmé : il le sera
     * par un accusé de l'Engine (étape 9), ou jamais, si le moteur a rejeté le message en silence.
     *
     * @param transport nom de la passerelle utilisée, pour le diagnostic
     */
    public void marquerPubliee(String transport, String destinataireReel) {
        this.statut = StatutEnvoi.PUBLIEE;
        this.statutEngine = transport;
        this.datePublication = LocalDateTime.now();
        this.prochaineTentative = null;
        this.derniereErreur = null;
        if (destinataireReel != null && !destinataireReel.equals(destinataire)) {
            // Redirection hors production (D10) : on garde trace de qui aurait été visé.
            this.destinataireOrigine = destinataire;
            this.destinataire = destinataireReel;
        }
    }

    /**
     * La remise a échoué. Tant qu'il reste des tentatives, la ligne repart plus tard ; sinon elle
     * passe en échec et devient visible en supervision (D13).
     *
     * @param prochaine échéance de la prochaine tentative, ou {@code null} s'il n'y en a plus
     */
    public void echouerTentative(String erreur, LocalDateTime prochaine) {
        this.tentatives++;
        this.derniereErreur = tronquer(erreur);
        this.prochaineTentative = prochaine;
        this.statut = prochaine == null ? StatutEnvoi.ECHEC : StatutEnvoi.EN_ATTENTE;
    }

    /**
     * Le moteur signale un échec définitif sur cette notification : plus rien ne partira, et la ligne
     * devient visible en supervision. Nos propres tentatives ne s'appliquent pas — c'est le moteur qui
     * a épuisé les siennes (Guide V1 §8).
     */
    public void marquerEchecDuMoteur(String erreur, LocalDateTime dateAccuse) {
        this.statut = StatutEnvoi.ECHEC;
        this.statutEngine = "FAILED";
        this.derniereErreur = tronquer(erreur);
        this.dateAccuse = dateAccuse;
        this.prochaineTentative = null;
    }

    /**
     * Aucun accusé n'est arrivé dans le délai attendu. L'envoi n'est ni confirmé ni échoué : il est
     * sans retour. C'est l'état d'un message rejeté en silence par le moteur — un jeton invalide, par
     * exemple —, et le compter comme délivré masquerait précisément ce défaut.
     */
    public void marquerSansRetour() {
        this.statut = StatutEnvoi.SANS_RETOUR;
        this.prochaineTentative = null;
    }

    /**
     * Notification trop ancienne pour être encore utile : elle n'est pas envoyée. Un rappel
     * d'audience remis trois jours après l'audience désinformerait son destinataire, et une file
     * accumulée pendant une coupure partirait d'un bloc.
     */
    public void perimer(String motif) {
        this.statut = StatutEnvoi.ECHEC;
        this.derniereErreur = tronquer(motif);
        this.prochaineTentative = null;
    }

    private static String tronquer(String texte) {
        if (texte == null) {
            return null;
        }
        return texte.length() <= 1000 ? texte : texte.substring(0, 1000);
    }

    /**
     * Accusé positif de l'Engine. Le texte des SMS et WhatsApp est alors effacé (D16) : une fois
     * délivré, il n'a plus d'usage, et seul le suivi est conservé.
     */
    public void marquerEnvoyee(String statutEngine, LocalDateTime dateAccuse) {
        this.statut = StatutEnvoi.ENVOYEE;
        this.statutEngine = statutEngine;
        this.dateAccuse = dateAccuse;
        this.prochaineTentative = null;
        if (canal == CanalEngine.SMS || canal == CanalEngine.WHATSAPP) {
            this.corps = null;
        }
    }

    private static NotificationEnvoi nouvelle(Origine origine, CanalEngine canal) {
        Objects.requireNonNull(origine, "origine");
        Objects.requireNonNull(canal, "canal");
        NotificationEnvoi envoi = new NotificationEnvoi();
        // Un canal par ligne : le canal complète la clé, sans quoi les canaux d'un même événement
        // se dédoublonneraient entre eux côté Engine.
        envoi.eventId = origine.eventId() + ":" + canal.name();
        envoi.typeEvenement = origine.typeEvenement();
        envoi.dossierId = origine.dossierId();
        envoi.alerteId = origine.alerteId();
        envoi.destinataireType = origine.destinataireType();
        envoi.destinataireRef = origine.destinataireRef();
        envoi.canal = canal;
        envoi.tentatives = 0;
        return envoi;
    }

    /**
     * Ce qui a provoqué la notification et à qui elle s'adresse, commun à tous les canaux d'un même
     * événement.
     *
     * @param eventId référence métier déterministe (par exemple {@code alerte-42}) ; le canal lui
     *                est ajouté pour chaque ligne
     */
    public record Origine(String eventId, String typeEvenement, Long dossierId, Long alerteId,
                          TypeDestinataire destinataireType, Long destinataireRef) {
        public Origine {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId obligatoire : il sert à la déduplication côté Engine");
            }
            if (typeEvenement == null || typeEvenement.isBlank()) {
                throw new IllegalArgumentException("typeEvenement obligatoire");
            }
            Objects.requireNonNull(destinataireType, "destinataireType");
        }
    }
}
