package com.afriland.audiences.notification.passerelle;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default gateway: logs the notification and sends nothing, so tests, CI and developer machines never
 * trigger a real SMS, e-mail or Teams message unless the Engine is deliberately enabled.
 * <p>
 * The log never contains the token, the message text or clear-text recipients (secret and personal data).
 */
@Slf4j
public class PasserelleJournal implements PasserelleNotificationEngine {

    /** Value written to {@code statut_engine}: the row was processed but nothing was sent. */
    public static final String NOM = "JOURNAL";

    @Override
    public String publier(MessageEngine message) {
        log.info("Notification Engine désactivé — message non envoyé : eventId={}, canaux={}, destinataire={}",
                message.eventId(), message.channels(), masquer(message.recipient()));
        return NOM;
    }

    static String masquer(Map<String, String> destinataires) {
        return destinataires.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + masquer(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /** Keeps the first two characters and, for an e-mail, the domain: "pa***@afrilandfirstbank.com". */
    static String masquer(String destinataire) {
        if (destinataire == null || destinataire.isBlank()) {
            return "(vide)";
        }
        int arobase = destinataire.indexOf('@');
        String local = arobase >= 0 ? destinataire.substring(0, arobase) : destinataire;
        String domaine = arobase >= 0 ? destinataire.substring(arobase) : "";
        String debut = local.length() > 2 ? local.substring(0, 2) : "";
        return debut + "***" + domaine;
    }
}
