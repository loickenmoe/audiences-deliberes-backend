package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface HistoriqueActionRepository extends JpaRepository<HistoriqueAction, Long>,
        JpaSpecificationExecutor<HistoriqueAction> {

    Page<HistoriqueAction> findByDossierIdOrderByDateActionDesc(Long dossierId, Pageable pageable);

    /**
     * Action kinds present in the whole history, for the audit screen filter. Independent of active
     * filters, and taken from the data so no option ever returns nothing.
     */
    @Query("select distinct h.code from HistoriqueAction h order by h.code")
    List<TypeActionHistorique> actionsPresentes();

    /** FR-GED-04 (daily report): number of distinct cases handled in the given range. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Activity per person over a period, counted by action kind: one row per person and code (the
     * service assembles), so the tracked kinds can change without rewriting the query. It counts
     * actions, not workload or difficulty.
     */
    @Query("select h.utilisateur.id as utilisateurId, h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.code in :codes and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.utilisateur.id, h.code")
    List<ActiviteParPersonne> compterActivitesParPersonne(@Param("codes") Collection<TypeActionHistorique> codes,
                                                          @Param("debut") LocalDateTime debut,
                                                          @Param("fin") LocalDateTime fin);

    /**
     * Average delay, in days, between a hearing and the entry of its report, per person.
     *
     * <p>Native query because the hearing link lives in the {@code jsonb} details
     * ({@code details->>'audienceId'}), which JPQL cannot read. Rows without {@code audienceId} are
     * ignored. {@code jsonb_exists} is used instead of the {@code ?} operator, which JDBC would treat
     * as a positional parameter and fail at runtime.
     */
    @Query(value = """
            SELECT h.utilisateur_id AS utilisateurId,
                   AVG(h.date_action::date - a.date_planifiee) AS delaiMoyen
            FROM historique_action h
            JOIN audience a ON a.id = (h.details ->> 'audienceId')::bigint
            WHERE h.code = 'AUDIENCE_COMPTE_RENDU_ENREGISTRE'
              AND jsonb_exists(h.details, 'audienceId')
              AND h.date_action >= :debut AND h.date_action < :fin
            GROUP BY h.utilisateur_id
            """, nativeQuery = true)
    List<DelaiSaisieParPersonne> delaiMoyenDeSaisieParPersonne(@Param("debut") LocalDateTime debut,
                                                               @Param("fin") LocalDateTime fin);

    /**
     * Activity of a single person, counted by action kind. Separate from
     * {@link #compterActivitesParPersonne} (management comparison on a few selected kinds) because a
     * person's sheet must reflect everything they did. Kinds with no action are not returned.
     */
    @Query("select h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.utilisateur.id = :utilisateurId and h.code in :codes "
            + "and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.code")
    List<ActiviteParCode> compterActivitesDe(@Param("utilisateurId") Long utilisateurId,
                                             @Param("codes") Collection<TypeActionHistorique> codes,
                                             @Param("debut") LocalDateTime debut,
                                             @Param("fin") LocalDateTime fin);

    /** The actions behind a count, newest first, so the related case can be opened. */
    @Query("select h from HistoriqueAction h "
            + "where h.utilisateur.id = :utilisateurId and h.code = :code "
            + "and h.dateAction >= :debut and h.dateAction < :fin "
            + "order by h.dateAction desc")
    Page<HistoriqueAction> listerActionsDe(@Param("utilisateurId") Long utilisateurId,
                                           @Param("code") TypeActionHistorique code,
                                           @Param("debut") LocalDateTime debut,
                                           @Param("fin") LocalDateTime fin,
                                           Pageable pageable);

    /**
     * Average report-entry delay of one person, in days; {@code null} (never zero) if they entered
     * no report. Same native-query constraints as {@link #delaiMoyenDeSaisieParPersonne}.
     */
    @Query(value = """
            SELECT AVG(h.date_action::date - a.date_planifiee)
            FROM historique_action h
            JOIN audience a ON a.id = (h.details ->> 'audienceId')::bigint
            WHERE h.code = 'AUDIENCE_COMPTE_RENDU_ENREGISTRE'
              AND h.utilisateur_id = :utilisateurId
              AND jsonb_exists(h.details, 'audienceId')
              AND h.date_action >= :debut AND h.date_action < :fin
            """, nativeQuery = true)
    Double delaiMoyenDeSaisieDe(@Param("utilisateurId") Long utilisateurId,
                                @Param("debut") LocalDateTime debut,
                                @Param("fin") LocalDateTime fin);

    /**
     * Lawyers' activity, counted per person and action kind (same shape as
     * {@link #compterActivitesParPersonne}). {@code keycloakId} and {@code identifiant} are returned
     * to find the directory entry without one extra query per lawyer.
     */
    @Query("select h.utilisateur.id as utilisateurId, h.utilisateur.nom as nom, "
            + "h.utilisateur.prenom as prenom, h.utilisateur.identifiant as identifiant, "
            + "h.utilisateur.keycloakId as keycloakId, h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.code in :codes and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.utilisateur.id, h.utilisateur.nom, h.utilisateur.prenom, "
            + "h.utilisateur.identifiant, h.utilisateur.keycloakId, h.code")
    List<ActiviteDeposee> compterDepotsParPersonne(@Param("codes") Collection<TypeActionHistorique> codes,
                                                   @Param("debut") LocalDateTime debut,
                                                   @Param("fin") LocalDateTime fin);

    /** One account, one filing kind, one count, with the keys to find the directory entry. */
    interface ActiviteDeposee {
        Long getUtilisateurId();

        String getNom();

        String getPrenom();

        String getIdentifiant();

        String getKeycloakId();

        TypeActionHistorique getCode();

        long getTotal();
    }

    /** One action kind and its count, for a person's sheet. */
    interface ActiviteParCode {
        TypeActionHistorique getCode();

        long getTotal();
    }

    /** One person, one action kind, one count. */
    interface ActiviteParPersonne {
        Long getUtilisateurId();

        TypeActionHistorique getCode();

        long getTotal();
    }

    /** One person and their average report-entry delay in days; null if they entered no report. */
    interface DelaiSaisieParPersonne {
        Long getUtilisateurId();

        Double getDelaiMoyen();
    }
}
