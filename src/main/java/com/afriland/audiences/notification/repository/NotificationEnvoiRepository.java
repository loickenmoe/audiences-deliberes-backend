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

    /**
     * Notifications remises au moteur avant {@code limite} et toujours sans accusé (Q-120). Un
     * message rejeté en silence n'a ni accusé de succès ni accusé d'échec : seul le temps le révèle.
     */
    @Query("SELECT n FROM NotificationEnvoi n WHERE n.statut = com.afriland.audiences.notification.domain.StatutEnvoi.PUBLIEE "
            + "AND n.datePublication <= :limite ORDER BY n.datePublication ASC")
    List<NotificationEnvoi> trouverPublieesSansAccuse(@Param("limite") LocalDateTime limite, Pageable page);

    /** Supervision (D17) : comptes par canal et par statut sur une fenêtre — un passage, pas un par canal. */
    @Query("SELECT n.canal, n.statut, COUNT(n) FROM NotificationEnvoi n "
            + "WHERE n.dateCreation >= :debut AND n.dateCreation < :fin GROUP BY n.canal, n.statut")
    List<Object[]> compterParCanalEtStatut(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Envois à examiner : abandonnés, restés sans accusé, ou dont le destinataire n'était pas
     * joignable. Les plus récents d'abord — ce sont ceux sur lesquels on peut encore agir.
     */
    @Query("SELECT n FROM NotificationEnvoi n WHERE n.dateCreation >= :debut AND n.dateCreation < :fin "
            + "AND n.statut IN (com.afriland.audiences.notification.domain.StatutEnvoi.ECHEC, "
            + "com.afriland.audiences.notification.domain.StatutEnvoi.SANS_RETOUR, "
            + "com.afriland.audiences.notification.domain.StatutEnvoi.NON_JOIGNABLE) "
            + "ORDER BY n.dateCreation DESC")
    List<NotificationEnvoi> trouverEnDefaut(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
                                            Pageable page);
}
