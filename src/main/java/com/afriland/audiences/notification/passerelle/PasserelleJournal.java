package com.afriland.audiences.notification.passerelle;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Passerelle par défaut : écrit la notification dans le journal applicatif et n'envoie rien. Elle
 * garantit que les tests, l'intégration continue et les postes de développement ne déclenchent
 * jamais un vrai SMS, courriel ou message Teams tant que l'Engine n'est pas activé volontairement.
 * <p>
 * Le journal ne porte ni le jeton, ni le texte du message, ni les destinataires en clair : ce sont
 * une signature et des coordonnées personnelles, qui n'ont pas à s'y trouver.
 */
@Slf4j
public class PasserelleJournal implements PasserelleNotificationEngine {

    /** Valeur inscrite dans {@code statut_engine} : la ligne a été traitée, mais rien n'est parti. */
    public static final String NOM = "JOURNAL";

    @Override
    public String publier(MessageEngine message) {
        log.info("Notification Engine désactivé — message non envoyé : eventId={}, canaux={}, destinataires={}",
                message.eventId(), message.channels(), masquer(message.channelRecipients()));
        return NOM;
    }

    static String masquer(Map<String, String> destinataires) {
        return destinataires.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + masquer(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /** Conserve les deux premiers caractères et, pour un courriel, le domaine : « pa***@afrilandfirstbank.com ». */
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
