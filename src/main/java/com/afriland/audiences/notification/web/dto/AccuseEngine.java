package com.afriland.audiences.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Accusé publié par le Notification Engine (Guide V1 §8). Les noms des champs sont ceux du moteur,
 * en anglais : c'est un contrat externe.
 * <p>
 * Les deux topics n'ont pas exactement la même forme — le succès porte {@code sentAt}, l'échec
 * {@code failedAt}, {@code errorMessage} et {@code permanent} — et un seul type les représente,
 * avec les champs manquants à {@code null}. Les champs inconnus sont ignorés : le moteur peut en
 * ajouter sans casser la lecture.
 *
 * @param eventId notre propre référence. <b>Absente du contrat actuel</b> : le moteur ne la renvoie
 *                pas. Elle est lue au cas où il le ferait un jour, ce qui permettrait enfin le
 *                rattachement ligne à ligne (Q-120).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccuseEngine(
        String notificationId,
        String workflowId,
        String serviceId,
        String channel,
        String provider,
        String providerMessageId,
        String errorMessage,
        Boolean permanent,
        OffsetDateTime sentAt,
        OffsetDateTime failedAt,
        String eventId) {

    /** Instant de l'événement côté moteur, ramené à l'heure locale des autres colonnes. */
    public LocalDateTime instant() {
        OffsetDateTime horodatage = sentAt != null ? sentAt : failedAt;
        return horodatage == null ? null : horodatage.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
