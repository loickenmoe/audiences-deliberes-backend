package com.afriland.audiences.notification.repository;

import com.afriland.audiences.notification.domain.NotificationEnvoi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationEnvoiRepository extends JpaRepository<NotificationEnvoi, Long> {

    /** Retrouve une ligne à partir de l'accusé de l'Engine, qui ne connaît que l'eventId. */
    Optional<NotificationEnvoi> findByEventId(String eventId);

    /**
     * Garde-fou avant écriture : le même événement peut se représenter (relance planifiée, avocat
     * réaffecté au même dossier). Sans ce contrôle, la contrainte d'unicité ferait échouer l'action
     * métier qui a provoqué la notification.
     */
    boolean existsByEventId(String eventId);
}
