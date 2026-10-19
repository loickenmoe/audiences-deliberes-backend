package com.afriland.audiences.notification.passerelle;

/**
 * Delivery channel of the bank's Notification Engine (Guide V1, {@code channels} field). Each
 * constant name is exactly the value expected by the engine, also used as key in
 * {@code channelRecipients}.
 * <p>
 * Unlike {@link com.afriland.audiences.alerte.domain.CanalAlerte} (application-side delivery), this
 * is the external transport. Outlook maps to {@link #EMAIL} sent to the work mailbox.
 */
public enum CanalEngine {
    EMAIL,
    SMS,
    WHATSAPP,
    TEAMS
}
