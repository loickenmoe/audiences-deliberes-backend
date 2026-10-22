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
 * A queued external notification: one recipient, one channel (migration V27).
 * <p>
 * Rows are only created through {@link #aRemettre} or {@link #nonJoignable}, never a public
 * constructor, so the invariant "recipient present unless NON_JOIGNABLE" holds in Java and not only
 * through the database constraint.
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

    /** Document to attach; loose reference, like {@code dossierId}. */
    @Column(name = "piece_jointe_document_id")
    private Long pieceJointeDocumentId;

    @Column(name = "piece_jointe_nom", length = 255)
    private String pieceJointeNom;

    @Column(name = "piece_jointe_mime", length = 100)
    private String pieceJointeMime;

    /** Path in our own storage, from which the file is read to upload it to the engine. */
    @Column(name = "piece_jointe_chemin", length = 500)
    private String pieceJointeChemin;

    /** Key returned by the engine once the file is uploaded; cited by the notification. */
    @Column(name = "piece_jointe_cle", length = 500)
    private String pieceJointeCle;

    /** Row ready to send: handed to the transport on the next dispatcher pass. */
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
     * Attaches a document to this notification. E-mail only: WhatsApp and Teams silently drop
     * attachments and SMS does not support them.
     */
    public void joindre(Long documentId, String nom, String mime, String chemin) {
        if (canal != CanalEngine.EMAIL) {
            return;
        }
        this.pieceJointeDocumentId = documentId;
        this.pieceJointeNom = nom;
        this.pieceJointeMime = mime;
        this.pieceJointeChemin = chemin;
    }

    /** An attachment is expected and its storage key is not known yet. */
    public boolean attendSaPieceJointe() {
        return pieceJointeChemin != null && pieceJointeCle == null;
    }

    /** The key was requested from the engine; the row waits for the response. */
    public void attendreLaPieceJointe(LocalDateTime relance) {
        this.statut = StatutEnvoi.ATTENTE_PIECE_JOINTE;
        this.prochaineTentative = relance;
    }

    /** The file is uploaded: the row resumes and will cite the key in its notification. */
    public void pieceJointeDeposee(String cleStockage) {
        this.pieceJointeCle = cleStockage;
        this.statut = StatutEnvoi.EN_ATTENTE;
        this.prochaineTentative = LocalDateTime.now();
        this.derniereErreur = null;
    }

    /**
     * The upload failed. The notification is sent <b>without</b> its attachment rather than never;
     * the missing document stays traced.
     */
    public void pieceJointeAbandonnee(String motif) {
        this.pieceJointeChemin = null;
        this.statut = StatutEnvoi.EN_ATTENTE;
        this.prochaineTentative = LocalDateTime.now();
        this.derniereErreur = tronquer(motif);
    }

    /**
     * Channel routed without a usable contact: nothing is sent, but the gap stays visible in monitoring
     * with its reason (missing or invalid contact) in {@code derniereErreur}.
     */
    public static NotificationEnvoi nonJoignable(Origine origine, CanalEngine canal, String motif) {
        NotificationEnvoi envoi = nouvelle(origine, canal);
        envoi.statut = StatutEnvoi.NON_JOIGNABLE;
        envoi.derniereErreur = motif;
        return envoi;
    }

    /**
     * The message was handed to the transport. Actual delivery is confirmed by an Engine receipt, or
     * never if the engine silently rejected the message.
     *
     * @param transport name of the gateway used, for diagnostics
     */
    public void marquerPubliee(String transport, String destinataireReel) {
        this.statut = StatutEnvoi.PUBLIEE;
        this.statutEngine = transport;
        this.datePublication = LocalDateTime.now();
        this.prochaineTentative = null;
        this.derniereErreur = null;
        if (destinataireReel != null && !destinataireReel.equals(destinataire)) {
            // Non-production redirection: keep track of the original recipient.
            this.destinataireOrigine = destinataire;
            this.destinataire = destinataireReel;
        }
    }

    /**
     * Delivery failed. While attempts remain the row is retried later; otherwise it fails and shows in
     * monitoring.
     *
     * @param prochaine time of the next attempt, or {@code null} if none remain
     */
    public void echouerTentative(String erreur, LocalDateTime prochaine) {
        this.tentatives++;
        this.derniereErreur = tronquer(erreur);
        this.prochaineTentative = prochaine;
        this.statut = prochaine == null ? StatutEnvoi.ECHEC : StatutEnvoi.EN_ATTENTE;
    }

    /**
     * The engine reports a permanent failure: nothing more is sent and the row shows in monitoring.
     * Our own retries do not apply, the engine has exhausted its own (Guide V1 §8).
     */
    public void marquerEchecDuMoteur(String erreur, LocalDateTime dateAccuse) {
        this.statut = StatutEnvoi.ECHEC;
        this.statutEngine = "FAILED";
        this.derniereErreur = tronquer(erreur);
        this.dateAccuse = dateAccuse;
        this.prochaineTentative = null;
    }

    /**
     * Requeued after correction, on the DJ's decision. The attempt counter is reset since this is a new,
     * human-initiated try.
     */
    public void relancer() {
        this.statut = StatutEnvoi.EN_ATTENTE;
        this.tentatives = 0;
        this.prochaineTentative = LocalDateTime.now();
        this.derniereErreur = null;
        this.statutEngine = null;
        this.datePublication = null;
    }

    /**
     * No receipt arrived within the expected delay: neither confirmed nor failed. This is the state of a
     * message silently rejected by the engine (e.g. invalid token); counting it as delivered would hide it.
     */
    public void marquerSansRetour() {
        this.statut = StatutEnvoi.SANS_RETOUR;
        this.prochaineTentative = null;
    }

    /**
     * Notification too old to be useful: it is not sent. A hearing reminder delivered after the hearing
     * would mislead, and a backlog built up during an outage would otherwise go out all at once.
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
     * Positive Engine receipt. SMS and WhatsApp text is then erased: once delivered it has no use, and
     * only tracking data is kept.
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
        // One channel per row: the channel completes the key, otherwise the Engine would deduplicate
        // the channels of one event against each other. "-" separator, not ":", as no accepted message
        // contains a colon in this reference.
        envoi.eventId = origine.eventId() + "-" + canal.name();
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
     * What triggered the notification and whom it targets, shared by all channels of an event.
     *
     * @param eventId deterministic business reference (e.g. {@code alerte-42}); the channel is
     *                appended for each row
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
