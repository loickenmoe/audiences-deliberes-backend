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

    /** FR-GED-02/UC-GED-02 (#43) : documents actifs d'un dossier, hors documents supprimés. */
    Page<Document> findByDossierIdAndEstSupprimeFalse(Long dossierId, Pageable pageable);

    /** FR-GED-04 : pièces ajoutées à une date donnée (rapport journalier). */
    long countByDateChargementBetween(LocalDateTime debut, LocalDateTime fin);

    /** La file du DJ et de la DJA : les partages proposés, le plus ancien d'abord (QF-64). */
    Page<Document> findByPartageAvocatAndEstSupprimeFalseOrderByPartageLeAsc(
            StatutPartageAvocat partageAvocat, Pageable pageable);

    /**
     * Les pièces d'un dossier partagées avec l'avocat (QF-64). La restriction est **dans la
     * requête** : un filtre appliqué après coup exposerait toute la GED du dossier s'il était
     * oublié une seule fois.
     */
    List<Document> findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(
            Long dossierId, StatutPartageAvocat partageAvocat);

    /** Une pièce précise, à la seule condition qu'elle soit de ce dossier et partagée (QF-64). */
    java.util.Optional<Document> findByIdAndDossierIdAndPartageAvocatAndEstSupprimeFalse(
            Long id, Long dossierId, StatutPartageAvocat partageAvocat);
}
