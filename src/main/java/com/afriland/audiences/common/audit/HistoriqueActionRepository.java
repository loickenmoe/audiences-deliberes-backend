package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
}
