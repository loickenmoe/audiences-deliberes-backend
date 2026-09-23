package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Message tel que le Notification Engine l'attend. Les noms des champs sont ceux du moteur, en
 * anglais : c'est un contrat externe, pas du code métier.
 * <p>
 * <b>Ce contrat vient d'un message réellement accepté</b>, transmis par l'administrateur, et non du
 * seul Guide V1 — qui s'en écarte sur trois points (Q-121) :
 * <ul>
 *   <li>{@code serviceKey}, absent du Guide, accompagne le jeton ;</li>
 *   <li>{@code priority} voyage <b>dans le message</b>, et non seulement sur le compte de service ;</li>
 *   <li>le destinataire est donné par {@code recipient}, que le Guide disait ignoré dès lors que
 *       {@code channelRecipients} est présent.</li>
 * </ul>
 * À l'inverse, {@code locale} et {@code metadata}, que le Guide documente, <b>ne figurent dans aucun
 * message accepté</b> : ils sont retirés. Un champ que le moteur n'attend pas peut suffire à le faire
 * rejeter le message, et un rejet ne laisse aucune trace.
 * Une ligne de notre file ne vise qu'un canal : {@code recipient} suffit, et
 * {@code channelRecipients} reste disponible pour un envoi multicanal ultérieur.
 * <p>
 * Les champs nuls ne sont pas sérialisés — un SMS n'a pas d'objet.
 *
 * @param token       jeton court signé HS256 qui identifie le service appelant ; sans lui, le moteur rejette
 * @param serviceKey  code du compte de service, répété hors du jeton
 * @param priority    priorité du message (celle du compte : STANDARD)
 * @param eventId     clé de déduplication : une republication ne double pas l'envoi
 * @param category    famille du message ; nos notifications sont transactionnelles
 * @param callbackUrl adresse rappelée par le moteur avec le statut ; nulle si l'application n'est pas
 *                    joignable depuis le cluster
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageEngine(
        String token,
        String serviceKey,
        String priority,
        List<String> channels,
        String recipient,
        Map<String, String> channelRecipients,
        String directSubject,
        String directBody,
        String eventId,
        String category,
        String callbackUrl,
        List<PieceJointeEngine> attachments) {

    public static final String CATEGORIE = "TRANSACTIONAL";

    public static MessageEngine de(String token, String serviceKey, String priority, CanalEngine canal,
                                   String destinataire, String sujet, String corps, String eventId,
                                   String callbackUrl) {
        return de(token, serviceKey, priority, canal, destinataire, sujet, corps, eventId, callbackUrl, null);
    }

    /**
     * @param pieceJointe déjà déposée chez le moteur ; {@code null} quand il n'y en a pas, auquel cas
     *                    le champ n'est pas sérialisé
     */
    public static MessageEngine de(String token, String serviceKey, String priority, CanalEngine canal,
                                   String destinataire, String sujet, String corps, String eventId,
                                   String callbackUrl, PieceJointeEngine pieceJointe) {
        return new MessageEngine(token, serviceKey, priority, List.of(canal.name()), destinataire, null,
                sujet, corps, eventId, CATEGORIE, callbackUrl,
                pieceJointe == null ? null : List.of(pieceJointe));
    }

    /** Le jeton porte la signature du service, et l'URL de rappel son jeton : ni l'un ni l'autre au journal. */
    public MessageEngine sansJeton() {
        return new MessageEngine(null, serviceKey, priority, channels, recipient, channelRecipients, directSubject,
                directBody, eventId, category, callbackUrl == null ? null : "(présente)", attachments);
    }
}
