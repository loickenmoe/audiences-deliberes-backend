package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Message tel que le Notification Engine l'attend (Guide V1 §5). Les noms des champs sont ceux du
 * Guide, en anglais : c'est un contrat externe, pas du code métier.
 * <p>
 * {@code channelRecipients} est toujours utilisé, même pour un seul canal : le Guide précise que
 * {@code recipient} est alors ignoré, et une ligne de notre file ne vise de toute façon qu'un canal.
 * Les champs nuls ne sont pas sérialisés — un SMS n'a pas d'objet.
 * <p>
 * <b>Deux champs portent notre référence</b>, parce que les accusés du moteur ne la renvoient pas
 * (Q-120) : {@code callbackUrl}, qu'il rappelle après traitement, et {@code metadata.correlationId},
 * au cas où il la restituerait un jour dans ses topics de suivi.
 *
 * @param token       jeton court signé HS256 qui identifie le service appelant ; sans lui, le moteur rejette
 * @param eventId     clé de déduplication : une republication ne double pas l'envoi
 * @param category    famille du message ; nos notifications sont transactionnelles
 * @param locale      langue du message
 * @param callbackUrl adresse rappelée par le moteur avec le statut ; nulle si l'application n'est pas
 *                    joignable depuis le cluster
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageEngine(
        String token,
        List<String> channels,
        Map<String, String> channelRecipients,
        String directSubject,
        String directBody,
        String eventId,
        String category,
        String locale,
        String callbackUrl,
        Map<String, String> metadata) {

    public static final String CATEGORIE = "TRANSACTIONAL";
    public static final String LOCALE = "fr_FR";
    public static final String CLE_CORRELATION = "correlationId";

    public static MessageEngine de(String token, CanalEngine canal, String destinataire, String sujet, String corps,
                                   String eventId, String callbackUrl) {
        return new MessageEngine(token, List.of(canal.name()), Map.of(canal.name(), destinataire),
                sujet, corps, eventId, CATEGORIE, LOCALE, callbackUrl, Map.of(CLE_CORRELATION, eventId));
    }

    /** Le jeton porte la signature du service, et l'URL de rappel son jeton : ni l'un ni l'autre au journal. */
    public MessageEngine sansJeton() {
        return new MessageEngine(null, channels, channelRecipients, directSubject, directBody, eventId, category,
                locale, callbackUrl == null ? null : "(présente)", metadata);
    }
}
