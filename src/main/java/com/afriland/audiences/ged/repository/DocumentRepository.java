package com.afriland.audiences.ged.repository;

import com.afriland.audiences.ged.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** FR-GED-02/UC-GED-02 (#43) : documents actifs d'un dossier, hors documents supprimés. */
    List<Document> findByDossierIdAndEstSupprimeFalse(Long dossierId);

    /** FR-GED-04 : pièces ajoutées à une date donnée (rapport journalier). */
    long countByDateChargementBetween(LocalDateTime debut, LocalDateTime fin);
}
