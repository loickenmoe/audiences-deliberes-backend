package com.afriland.audiences.ged.repository;

import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.StatutSuppression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeSuppressionRepository extends JpaRepository<DemandeSuppression, Long> {

    Optional<DemandeSuppression> findByDocumentIdAndStatut(Long documentId, StatutSuppression statut);

    boolean existsByDocumentIdAndStatut(Long documentId, StatutSuppression statut);

    /** UC-GED-03 alt. 6-a: pending requests never reminded, older than the delay. */
    List<DemandeSuppression> findByStatutAndDateDerniereRelanceIsNullAndDateDemandeLessThanEqual(
            StatutSuppression statut, LocalDateTime seuil);

    /** Pending requests already reminded once, whose last reminder is older than the delay. */
    List<DemandeSuppression> findByStatutAndDateDerniereRelanceLessThanEqual(StatutSuppression statut, LocalDateTime seuil);

    /** The DJ/DJA queue of pending deletion requests, oldest first. */
    Page<DemandeSuppression> findByStatutOrderByDateDemandeAsc(StatutSuppression statut, Pageable pageable);

    Page<DemandeSuppression> findByOrderByDateDemandeDesc(Pageable pageable);
}
