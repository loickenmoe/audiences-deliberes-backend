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
     * Natures d'action présentes dans tout l'historique (Q-112), pour le filtre de l'écran d'audit.
     *
     * <p>Volontairement <b>indépendante des filtres en cours</b> : une liste de choix qui se réduit
     * à mesure qu'on filtre empêche de changer d'avis sans tout vider. Elle vient de la donnée et
     * non de l'énumération complète, pour ne jamais proposer une nature qui ne renverrait rien.
     */
    @Query("select distinct h.code from HistoriqueAction h order by h.code")
    List<TypeActionHistorique> actionsPresentes();

    /** FR-GED-04 (rapport journalier) : nombre de dossiers distincts manipulés à une date donnée. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Observation 14 : l'activité de chacun sur une période, comptée par nature d'action.
     *
     * <p><b>Une ligne par personne et par code</b>, et non une colonne par indicateur : la liste des
     * natures suivies changera, et une requête qui les nomme une à une devrait être réécrite à
     * chaque fois. Le service assemble.
     *
     * <p><b>Ce que cette requête ne mesure pas, et ne peut pas mesurer.</b> Elle compte des
     * <b>actions</b>, pas du travail : l'application n'horodate pas des durées, et rien ici ne dit
     * si un dossier était simple ou difficile. C'est la limite posée avec le métier (N11) - on
     * compte, on n'apprécie pas.
     */
    @Query("select h.utilisateur.id as utilisateurId, h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.code in :codes and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.utilisateur.id, h.code")
    List<ActiviteParPersonne> compterActivitesParPersonne(@Param("codes") Collection<TypeActionHistorique> codes,
                                                          @Param("debut") LocalDateTime debut,
                                                          @Param("fin") LocalDateTime fin);

    /**
     * Le délai moyen, en jours, entre une audience et la saisie de son compte rendu.
     *
     * <p><b>Requête native, et il faut dire pourquoi.</b> Le lien entre l'action et l'audience passe
     * par le {@code jsonb} des détails : {@code details->>'audienceId'}. JPQL ne sait pas lire un
     * document JSON, et dupliquer l'identifiant dans une colonne pour les besoins d'un seul rapport
     * ferait payer à toute l'écriture d'audit le prix d'une lecture.
     *
     * <p>Les détails sans {@code audienceId} - il n'y en a pas pour ce code, mais une donnée
     * ancienne pourrait en manquer - sont ignorés plutôt que de fausser la moyenne.
     *
     * <p><b>{@code jsonb_exists} et non l'opérateur {@code ?}.</b> PostgreSQL écrit cette présence
     * {@code details ? 'audienceId'}, mais {@code ?} est le marqueur de paramètre positionnel de
     * JDBC : le pilote le remplacerait par une valeur, et la requête échouerait à l'exécution -
     * jamais à la compilation. La fonction fait la même chose sans cette ambiguïté.
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

    /** Une personne, une nature d'action, un décompte. */
    interface ActiviteParPersonne {
        Long getUtilisateurId();

        TypeActionHistorique getCode();

        long getTotal();
    }

    /** Une personne et son délai moyen de saisie, en jours. Nul si elle n'a saisi aucun compte rendu. */
    interface DelaiSaisieParPersonne {
        Long getUtilisateurId();

        Double getDelaiMoyen();
    }
}
