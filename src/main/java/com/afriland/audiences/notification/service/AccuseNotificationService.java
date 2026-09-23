package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationAccuse;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.repository.NotificationAccuseRepository;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Enregistre les accusés du Notification Engine (topics {@code notification-sent} et
 * {@code notification-failed}), quelle que soit la voie par laquelle ils arrivent.
 * <p>
 * <b>Deux garde-fous imposés par le contrat du moteur.</b>
 * <ul>
 *   <li><b>Filtrage par service</b> : les topics portent les accusés de tous les services de la
 *       banque. On ne retient que les nôtres, sur le {@code serviceId}.</li>
 *   <li><b>Livraison au moins une fois</b> : le même accusé peut arriver deux fois. On l'ignore
 *       silencieusement à la seconde, grâce à l'unicité (notificationId, type).</li>
 * </ul>
 * <b>Ce que l'accusé ne dit pas</b> : à quelle ligne de notre file il correspond (Q-120). Il est donc
 * enregistré tel quel, sans rattachement, et sert la mesure par canal et par fournisseur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccuseNotificationService {

    private final NotificationAccuseRepository notificationAccuseRepository;
    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final NotificationEngineProperties proprietes;

    /**
     * @return {@code true} si l'accusé nous concernait et a été enregistré
     */
    @Transactional
    public boolean enregistrer(AccuseEngine accuse, TypeAccuse type) {
        if (accuse == null || accuse.notificationId() == null || accuse.notificationId().isBlank()) {
            log.warn("Accusé {} sans identifiant de notification : ignoré", type);
            return false;
        }
        if (!nousConcerne(accuse)) {
            return false;
        }
        if (notificationAccuseRepository.existsByNotificationIdAndType(accuse.notificationId(), type)) {
            // Livraison au moins une fois : le doublon est normal, pas une anomalie.
            log.debug("Accusé {} déjà enregistré pour {}", type, accuse.notificationId());
            return false;
        }
        NotificationAccuse enregistre = notificationAccuseRepository.save(NotificationAccuse.de(
                accuse.notificationId(), type, accuse.serviceId(), accuse.channel(), accuse.provider(),
                accuse.errorMessage(), accuse.permanent(), accuse.instant()));

        if (type == TypeAccuse.FAILED) {
            log.warn("Le moteur signale un échec sur le canal {} (fournisseur {}) : {}",
                    accuse.channel(), accuse.provider(), accuse.errorMessage());
        }
        rattacher(enregistre, accuse);
        return true;
    }

    /**
     * Rattache l'accusé à sa ligne d'envoi <b>si le moteur nous en donne le moyen</b> : aujourd'hui,
     * il ne renvoie pas l'{@code eventId}. Le champ reste donc vide, et ce code s'activera de
     * lui-même le jour où l'accusé le portera (Q-120).
     */
    private void rattacher(NotificationAccuse accuseEnregistre, AccuseEngine accuse) {
        if (accuse.eventId() == null || accuse.eventId().isBlank()) {
            return;
        }
        notificationEnvoiRepository.findByEventId(accuse.eventId()).ifPresent(envoi -> {
            accuseEnregistre.rattacherA(envoi.getId());
            if (accuseEnregistre.getType() == TypeAccuse.SENT) {
                envoi.marquerEnvoyee(accuse.provider(), accuse.instant());
            } else {
                envoi.marquerEchecDuMoteur(accuse.errorMessage(), accuse.instant());
            }
        });
    }

    /**
     * Rappel reçu sur l'URL que nous avions transmise ({@code callbackUrl}) : la référence est connue,
     * donc le rattachement est exact. C'est la seule voie qui l'autorise aujourd'hui (Q-120).
     * <p>
     * L'issue se lit sur le corps du rappel quand il en porte une ; le Guide n'en fixe pas la forme,
     * aussi l'absence d'erreur vaut succès — c'est ce qu'il décrit : « le statut (succès/échec) ».
     */
    @Transactional
    public void enregistrerRappel(String eventId, AccuseEngine accuse) {
        boolean echec = accuse != null
                && (accuse.errorMessage() != null || Boolean.TRUE.equals(accuse.permanent()) || accuse.failedAt() != null);
        TypeAccuse type = echec ? TypeAccuse.FAILED : TypeAccuse.SENT;

        notificationEnvoiRepository.findByEventId(eventId).ifPresentOrElse(envoi -> {
            if (echec) {
                envoi.marquerEchecDuMoteur(accuse.errorMessage(), accuse.instant());
            } else {
                envoi.marquerEnvoyee(accuse == null ? "CALLBACK" : accuse.provider(),
                        accuse == null ? LocalDateTime.now() : accuse.instant());
            }
            enregistrerSiNouveau(accuse, type, eventId, envoi.getId());
        }, () -> log.warn("Rappel {} reçu pour une référence inconnue : {}", type, eventId));
    }

    private void enregistrerSiNouveau(AccuseEngine accuse, TypeAccuse type, String eventId, Long envoiId) {
        // Sans identifiant du moteur, la référence tient lieu de clé : le rappel reste idempotent.
        String notificationId = accuse != null && accuse.notificationId() != null ? accuse.notificationId() : eventId;
        if (notificationAccuseRepository.existsByNotificationIdAndType(notificationId, type)) {
            return;
        }
        NotificationAccuse enregistre = notificationAccuseRepository.save(NotificationAccuse.de(notificationId, type,
                proprietes.codeService(), accuse == null ? null : accuse.channel(), accuse == null ? null : accuse.provider(),
                accuse == null ? null : accuse.errorMessage(), accuse == null ? null : accuse.permanent(),
                accuse == null ? LocalDateTime.now() : accuse.instant()));
        enregistre.rattacherA(envoiId);
    }

    /** Les topics sont partagés par tous les services de la banque (Guide V1 §8). */
    private boolean nousConcerne(AccuseEngine accuse) {
        String notre = proprietes.codeService();
        if (notre == null || notre.isBlank() || accuse.serviceId() == null) {
            // Sans code de service configuré, on ne peut rien affirmer : on n'enregistre pas.
            return false;
        }
        return notre.equals(accuse.serviceId());
    }
}
