package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeConstitutionRepository extends JpaRepository<DemandeConstitution, Long> {

    /** Line manager's "appointments to sign" work queue, filterable by status. */
    Page<DemandeConstitution> findByStatutOrderByDateDemandeAsc(StatutConstitution statut, Pageable pageable);

    Page<DemandeConstitution> findByOrderByDateDemandeDesc(Pageable pageable);

    /** Lawyer portal: appointment requests naming this lawyer as provider. */
    List<DemandeConstitution> findByPrestataireIdOrderByDateDemandeDesc(Long prestataireId);

    /**
     * The request whose signed letter is this document. Used for printing on pre-printed paper,
     * since a case's documents tab only knows the document, not the request that produced it.
     */
    Optional<DemandeConstitution> findByLettreDocumentId(Long documentId);

    /**
     * Whether this lawyer is formally appointed on this case. Assignment alone is not a mandate:
     * only the letter signed by the line manager makes the lawyer the bank's representative, and
     * only then do the case documents and notes open up.
     */
    boolean existsByDossierIdAndPrestataireIdAndStatut(Long dossierId, Long prestataireId,
                                                      com.afriland.audiences.intervenant.domain.StatutConstitution statut);
}
