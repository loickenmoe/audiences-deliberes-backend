package com.afriland.audiences.alerte.domain;

/**
 * Issue de la diffusion temps réel d'une alerte sur le canal applicatif (Q-109, NF-ALR-02).
 *
 * <p><b>Pourquoi trois valeurs et non un booléen.</b>
 * {@code SimpMessagingTemplate.convertAndSendToUser} ne lève aucune exception lorsque le
 * destinataire est déconnecté : elle route vers une file à laquelle personne n'est abonné, puis
 * retourne normalement. Un indicateur « poussée réussie » afficherait donc 100 % en permanence,
 * panne comprise — une mesure qui ment est pire que pas de mesure du tout. Il faut distinguer
 * l'absence de destinataire, qui est normale, de la rupture du canal, qui est le défaut.
 *
 * <p><b>Ce qui compte comme délivré.</b> {@link #DIFFUSEE} et {@link #DIFFEREE}. Le canal
 * applicatif de NF-ALR-02 ne se réduit pas à la poussée temps réel : il comprend la liste des
 * notifications, alimentée inconditionnellement puisque l'alerte est persistée avant toute
 * tentative de diffusion. Seul {@link #ECHEC} est une perte.
 */
public enum StatutDiffusion {

    /** Le destinataire avait une session WebSocket vivante et la poussée a abouti. */
    DIFFUSEE,

    /**
     * Aucune session ouverte au moment du déclenchement : l'alerte attend dans
     * {@code GET /alertes/mes-notifications}. Ce n'est pas un échec — personne ne reste connecté
     * en permanence, et le repli est garanti.
     */
    DIFFEREE,

    /**
     * La poussée a échoué : courtier indisponible, transport rompu. C'est le seul cas où le canal
     * applicatif n'a pas fait son travail, et donc le seul que NF-ALR-02 compte contre nous.
     */
    ECHEC
}
