package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.DossierAvocatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DossierAvocatRepository extends JpaRepository<DossierAvocat, DossierAvocatId> {

    List<DossierAvocat> findByDossierId(Long dossierId);

    boolean existsByDossierIdAndAvocatId(Long dossierId, Long avocatId);

    void deleteByDossierId(Long dossierId);
}
