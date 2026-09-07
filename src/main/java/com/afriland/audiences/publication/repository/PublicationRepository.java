package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    /** M15 (Q-68) : file de travail « publications à valider » de l'Assistante, filtrable par statut. */
    Page<Publication> findByStatutValidation(StatutPublication statut, Pageable pageable);

    /** M15 (Q-68) : publications d'un dossier — onglet correspondant de la fiche dossier. */
    Page<Publication> findByDossierId(Long dossierId, Pageable pageable);

    Page<Publication> findByDossierIdAndStatutValidation(Long dossierId, StatutPublication statut, Pageable pageable);
}
