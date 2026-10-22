package com.afriland.audiences.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Receipt published by the Notification Engine (Guide V1 §8); field names follow the external contract.
 * <p>
 * Success carries {@code sentAt}; failure carries {@code failedAt}, {@code errorMessage} and
 * {@code permanent}. One type covers both, missing fields being {@code null}. Unknown fields are
 * ignored so the engine can add some without breaking deserialization.
 *
 * @param eventId our own reference. Not in the current contract: read in case the engine returns it
 *                some day, which would allow row-level matching.
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

    /** Engine-side event time, converted to the local time used by the other columns. */
    public LocalDateTime instant() {
        OffsetDateTime horodatage = sentAt != null ? sentAt : failedAt;
        return horodatage == null ? null : horodatage.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
