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

    /** Q-74 : toutes les demandes de seuil d'un dossier, la plus récente d'abord. */
    List<AuditSeuilDossier> findByDossierIdOrderByDateActionDesc(Long dossierId);

    /** Q-74 : même liste, restreinte à un statut — `EN_ATTENTE` pour la file d'arbitrage. */
    List<AuditSeuilDossier> findByDossierIdAndStatutOrderByDateActionDesc(Long dossierId, StatutValidation statut);
}
