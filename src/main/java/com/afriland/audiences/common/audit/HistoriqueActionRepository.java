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
     * Natures d'action réellement présentes hors dossier (Q-110, typées en Q-111), pour alimenter un
     * filtre à <b>liste fermée</b>.
     *
     * <p>La liste vient de la donnée et non d'une énumération recopiée côté écran : elle ne propose
     * jamais un choix qui ne renverrait rien. Depuis Q-111 elle porte des codes et non des phrases,
     * si bien qu'une clé de configuration ne produit plus une entrée par paramètre.
     */
    @Query("select distinct h.code from HistoriqueAction h where h.dossierId is null order by h.code")
    List<TypeActionHistorique> actionsHorsDossier();

    /** FR-GED-04 (rapport journalier) : nombre de dossiers distincts manipulés à une date donnée. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
