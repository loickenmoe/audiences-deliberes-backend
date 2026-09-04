package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.DossierJuristeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DossierJuristeRepository extends JpaRepository<DossierJuriste, DossierJuristeId> {

    List<DossierJuriste> findByDossierId(Long dossierId);

    boolean existsByDossierIdAndUtilisateurId(Long dossierId, Long utilisateurId);

    void deleteByDossierId(Long dossierId);
}
