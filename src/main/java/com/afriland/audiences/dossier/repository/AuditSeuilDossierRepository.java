package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.domain.StatutValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditSeuilDossierRepository extends JpaRepository<AuditSeuilDossier, Long> {

    Optional<AuditSeuilDossier> findFirstByDossierIdAndStatutOrderByDateActionAsc(Long dossierId, StatutValidation statut);

    /** All threshold requests of a case, most recent first. */
    List<AuditSeuilDossier> findByDossierIdOrderByDateActionDesc(Long dossierId);

    /** Same list restricted to a status ({@code EN_ATTENTE} for the arbitration queue). */
    List<AuditSeuilDossier> findByDossierIdAndStatutOrderByDateActionDesc(Long dossierId, StatutValidation statut);
}
