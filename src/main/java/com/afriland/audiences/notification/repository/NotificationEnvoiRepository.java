package com.afriland.audiences.notification.repository;

import com.afriland.audiences.notification.domain.NotificationEnvoi;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Notifications à remettre, les plus anciennes d'abord : seuls les identifiants, car chacune est
     * ensuite traitée dans sa propre transaction. L'index partiel de V27 sert exactement cette requête.
     */
    @Query("SELECT n.id FROM NotificationEnvoi n WHERE n.statut = com.afriland.audiences.notification.domain.StatutEnvoi.EN_ATTENTE "
            + "AND n.prochaineTentative <= :maintenant ORDER BY n.prochaineTentative ASC")
    List<Long> trouverARemettre(@Param("maintenant") LocalDateTime maintenant, Pageable page);
}
