package com.afriland.audiences.ged.repository;

import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.StatutSuppression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeSuppressionRepository extends JpaRepository<DemandeSuppression, Long> {

    Optional<DemandeSuppression> findByDocumentIdAndStatut(Long documentId, StatutSuppression statut);

    boolean existsByDocumentIdAndStatut(Long documentId, StatutSuppression statut);

    /** UC-GED-03 alt. 6-a : demandes encore en attente et jamais relancées, au-delà du délai. */
    List<DemandeSuppression> findByStatutAndDateDerniereRelanceIsNullAndDateDemandeLessThanEqual(
            StatutSuppression statut, LocalDateTime seuil);

    /** Demandes en attente déjà relancées une fois, mais dont la dernière relance dépasse le délai. */
    List<DemandeSuppression> findByStatutAndDateDerniereRelanceLessThanEqual(StatutSuppression statut, LocalDateTime seuil);

    /** M15 (Q-68) : file de travail « demandes de suppression en attente » du DJ/DJA, plus ancienne d'abord. */
    List<DemandeSuppression> findByStatutOrderByDateDemandeAsc(StatutSuppression statut);

    List<DemandeSuppression> findByOrderByDateDemandeDesc();
}
