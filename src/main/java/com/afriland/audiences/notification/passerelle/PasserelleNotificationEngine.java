package com.afriland.audiences.notification.passerelle;

/**
 * Point unique de remise d'un message au Notification Engine. Le transport (sidecar Dapr ou Kafka
 * direct) est un détail d'implémentation : le reste de l'application ne dépend que de cette
 * interface, ce qui permet d'en changer par simple configuration.
 * <p>
 * Tant que l'interrupteur {@code notification.engine.actif} est à {@code false} (valeur par défaut),
 * l'implémentation retenue est {@link PasserelleJournal}, qui n'envoie rien.
 */
public interface PasserelleNotificationEngine {

    /**
     * Remet le message au transport. Une exception signifie que la remise a échoué et qu'elle peut
     * être retentée ; le suivi de la délivrance effective se fait à part (topics
     * {@code notification-sent} et {@code notification-failed}).
     *
     * @return nom du transport utilisé, consigné dans la file pour le diagnostic
     */
    String publier(MessageEngine message);

    /**
     * Demande au moteur une URL de dépôt pour une pièce jointe (Guide V1 §3 bis). La réponse arrive
     * plus tard, sur un topic, et porte le {@code requestId} de la demande.
     *
     * @return {@code false} si ce transport ne sait pas demander de dépôt — la notification partira
     *         alors sans sa pièce jointe, plutôt que de ne jamais partir
     */
    default boolean demanderDepot(DemandeDepotPieceJointe demande) {
        return false;
    }
}
