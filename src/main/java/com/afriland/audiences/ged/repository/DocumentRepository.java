package com.afriland.audiences.ged.repository;

import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** FR-GED-02/UC-GED-02: active documents of a case file, excluding deleted ones. */
    Page<Document> findByDossierIdAndEstSupprimeFalse(Long dossierId, Pageable pageable);

    /** FR-GED-04: documents added on a given date (daily report). */
    long countByDateChargementBetween(LocalDateTime debut, LocalDateTime fin);

    /** The DJ/DJA queue: proposed shares, oldest first. */
    Page<Document> findByPartageAvocatAndEstSupprimeFalseOrderByPartageLeAsc(
            StatutPartageAvocat partageAvocat, Pageable pageable);

    /**
     * Documents of a case file shared with the lawyer. The restriction lives <b>in the query</b>:
     * a filter applied afterwards would expose the whole case file's GED if it were forgotten once.
     */
    List<Document> findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(
            Long dossierId, StatutPartageAvocat partageAvocat);

    /** A single document, only if it belongs to this case file and is shared. */
    java.util.Optional<Document> findByIdAndDossierIdAndPartageAvocatAndEstSupprimeFalse(
            Long id, Long dossierId, StatutPartageAvocat partageAvocat);
}
