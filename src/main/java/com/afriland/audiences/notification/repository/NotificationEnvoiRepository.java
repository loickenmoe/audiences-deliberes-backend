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

    /** Finds a row from an Engine receipt, which only knows the eventId. */
    Optional<NotificationEnvoi> findByEventId(String eventId);

    /**
     * Rows waiting too long for their attachment: the upload request is retried, or the notification
     * is sent without the document.
     */
    @Query("SELECT n FROM NotificationEnvoi n WHERE n.statut = com.afriland.audiences.notification.domain.StatutEnvoi.ATTENTE_PIECE_JOINTE "
            + "AND n.prochaineTentative <= :maintenant ORDER BY n.prochaineTentative ASC")
    List<NotificationEnvoi> trouverEnAttenteDePieceJointe(@Param("maintenant") LocalDateTime maintenant, Pageable page);

    /**
     * Pre-write guard: the same event may recur (scheduled reminder, lawyer reassigned to the same
     * case file). Without it, the unique constraint would fail the triggering business action.
     */
    boolean existsByEventId(String eventId);

    /**
     * Notifications to deliver, oldest first. Only ids, since each is then processed in its own
     * transaction. Served by the V27 partial index.
     */
    @Query("SELECT n.id FROM NotificationEnvoi n WHERE n.statut = com.afriland.audiences.notification.domain.StatutEnvoi.EN_ATTENTE "
            + "AND n.prochaineTentative <= :maintenant ORDER BY n.prochaineTentative ASC")
    List<Long> trouverARemettre(@Param("maintenant") LocalDateTime maintenant, Pageable page);

    /**
     * Notifications delivered to the engine before {@code limite} and still without a receipt. A
     * silently rejected message gets neither success nor failure receipt; only time reveals it.
     */
    @Query("SELECT n FROM NotificationEnvoi n WHERE n.statut = com.afriland.audiences.notification.domain.StatutEnvoi.PUBLIEE "
            + "AND n.datePublication <= :limite ORDER BY n.datePublication ASC")
    List<NotificationEnvoi> trouverPublieesSansAccuse(@Param("limite") LocalDateTime limite, Pageable page);

    /** Monitoring: counts per channel and status over a window, in a single query. */
    @Query("SELECT n.canal, n.statut, COUNT(n) FROM NotificationEnvoi n "
            + "WHERE n.dateCreation >= :debut AND n.dateCreation < :fin GROUP BY n.canal, n.statut")
    List<Object[]> compterParCanalEtStatut(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Sends needing review: abandoned, without receipt, or with an unreachable recipient. Newest first,
     * as those can still be acted upon.
     */
    @Query("SELECT n FROM NotificationEnvoi n WHERE n.dateCreation >= :debut AND n.dateCreation < :fin "
            + "AND n.statut IN (com.afriland.audiences.notification.domain.StatutEnvoi.ECHEC, "
            + "com.afriland.audiences.notification.domain.StatutEnvoi.SANS_RETOUR, "
            + "com.afriland.audiences.notification.domain.StatutEnvoi.NON_JOIGNABLE) "
            + "ORDER BY n.dateCreation DESC")
    List<NotificationEnvoi> trouverEnDefaut(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
                                            Pageable page);
}
