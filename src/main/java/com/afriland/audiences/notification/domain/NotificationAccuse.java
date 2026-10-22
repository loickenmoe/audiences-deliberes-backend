package com.afriland.audiences.notification.domain;

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

/**
 * Receipt published by the Notification Engine on {@code notification-sent} or
 * {@code notification-failed} (migration V30).
 * <p>
 * It does not identify our send: the engine links it to its own {@code notificationId}, never to our
 * {@code eventId}. These rows measure successes and failures per channel and provider, but not which
 * queued notification succeeded. {@code notificationEnvoiId} is reserved for when that link becomes possible.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_accuse")
public class NotificationAccuse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, length = 100)
    private String notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeAccuse type;

    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Column(length = 20)
    private String canal;

    @Column(length = 100)
    private String fournisseur;

    @Column(name = "message_erreur", length = 1000)
    private String messageErreur;

    private Boolean definitif;

    @Column(name = "date_evenement")
    private LocalDateTime dateEvenement;

    @CreationTimestamp
    @Column(name = "date_reception", nullable = false, updatable = false)
    private LocalDateTime dateReception;

    @Column(name = "notification_envoi_id")
    private Long notificationEnvoiId;

    public static NotificationAccuse de(String notificationId, TypeAccuse type, String serviceId,
                                        LocalDateTime dateEvenement) {
        NotificationAccuse accuse = new NotificationAccuse();
        accuse.notificationId = notificationId;
        accuse.type = type;
        accuse.serviceId = serviceId;
        accuse.dateEvenement = dateEvenement;
        return accuse;
    }

    /** What the engine reports about the delivery; all optional. */
    public NotificationAccuse avecDetails(String canal, String fournisseur, String messageErreur, Boolean definitif) {
        this.canal = canal;
        this.fournisseur = fournisseur;
        this.messageErreur = tronquer(messageErreur);
        this.definitif = definitif;
        return this;
    }

    /** Links the receipt to a queue row, once the engine provides the means to do so. */
    public void rattacherA(Long notificationEnvoiId) {
        this.notificationEnvoiId = notificationEnvoiId;
    }

    private static String tronquer(String texte) {
        if (texte == null) {
            return null;
        }
        return texte.length() <= 1000 ? texte : texte.substring(0, 1000);
    }

    public enum TypeAccuse {
        SENT, FAILED
    }
}
