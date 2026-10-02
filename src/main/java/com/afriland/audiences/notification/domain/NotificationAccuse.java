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
 * Accusé publié par le Notification Engine sur {@code notification-sent} ou
 * {@code notification-failed} (voir la migration V30).
 * <p>
 * <b>Il n'identifie pas notre envoi.</b> Le moteur le rattache à son propre {@code notificationId},
 * jamais à l'{@code eventId} que nous avons fourni. Ces lignes permettent donc de mesurer ce qui part
 * et ce qui échoue, par canal et par fournisseur, mais pas de dire quelle notification de la file a
 * abouti. Le champ {@code notificationEnvoiId} attend que ce rattachement devienne possible (Q-120).
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

    public static NotificationAccuse de(String notificationId, TypeAccuse type, String serviceId, String canal,
                                        String fournisseur, String messageErreur, Boolean definitif,
                                        LocalDateTime dateEvenement) {
        NotificationAccuse accuse = new NotificationAccuse();
        accuse.notificationId = notificationId;
        accuse.type = type;
        accuse.serviceId = serviceId;
        accuse.canal = canal;
        accuse.fournisseur = fournisseur;
        accuse.messageErreur = tronquer(messageErreur);
        accuse.definitif = definitif;
        accuse.dateEvenement = dateEvenement;
        return accuse;
    }

    /** Rattache l'accusé à une ligne de la file, le jour où le moteur nous en donnera le moyen. */
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
