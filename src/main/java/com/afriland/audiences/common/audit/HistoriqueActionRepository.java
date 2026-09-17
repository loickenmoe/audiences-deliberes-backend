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
     * Libellés d'action réellement présents hors dossier (Q-110), pour alimenter un filtre à
     * <b>liste fermée</b>.
     *
     * <p>Sans code d'action typé (QF-24), c'est le seul moyen honnête d'offrir un choix : la liste
     * vient de la donnée, pas d'une énumération recopiée côté écran, qui divergerait au premier
     * libellé ajouté. Elle reste courte par nature — une entrée par clé de configuration, plus les
     * quelques actions de référentiel.
     */
    @Query("select distinct h.action from HistoriqueAction h where h.dossierId is null order by h.action")
    List<String> actionsHorsDossier();

    /** FR-GED-04 (rapport journalier) : nombre de dossiers distincts manipulés à une date donnée. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
