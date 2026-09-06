package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface HistoriqueActionRepository extends JpaRepository<HistoriqueAction, Long> {

    Page<HistoriqueAction> findByDossierIdOrderByDateActionDesc(Long dossierId, Pageable pageable);

    /** FR-GED-04 (rapport journalier) : nombre de dossiers distincts manipulés à une date donnée. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
