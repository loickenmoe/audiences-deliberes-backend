package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.DossierJuristeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DossierJuristeRepository extends JpaRepository<DossierJuriste, DossierJuristeId> {

    List<DossierJuriste> findByDossierId(Long dossierId);

    /** Affectations de plusieurs dossiers en une requête (QF-41). */
    List<DossierJuriste> findByDossierIdIn(Collection<Long> dossierIds);

    boolean existsByDossierIdAndUtilisateurId(Long dossierId, Long utilisateurId);

    void deleteByDossierId(Long dossierId);
}
