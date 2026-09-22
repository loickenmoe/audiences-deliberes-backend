package com.afriland.audiences.notification.passerelle;

/**
 * Point unique de remise d'une notification au Notification Engine. Le transport (sidecar Dapr ou
 * Kafka direct) est un détail d'implémentation : le reste de l'application ne dépend que de cette
 * interface, ce qui permet d'en changer par simple configuration.
 * <p>
 * Tant que l'interrupteur {@code notification.engine.actif} est à {@code false} (valeur par défaut),
 * l'implémentation retenue est {@link PasserelleJournal}, qui n'envoie rien.
 */
public interface PasserelleNotificationEngine {

    /**
     * Remet la notification au transport. Une exception signifie que la remise a échoué et qu'elle
     * peut être retentée ; le suivi de la délivrance effective se fait à part (topics
     * {@code notification-sent} et {@code notification-failed}).
     */
    void publier(DemandeNotification demande);
}
