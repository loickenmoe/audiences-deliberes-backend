package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.domain.StatutValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditSeuilDossierRepository extends JpaRepository<AuditSeuilDossier, Long> {

    Optional<AuditSeuilDossier> findFirstByDossierIdAndStatutOrderByDateActionAsc(Long dossierId, StatutValidation statut);
}
