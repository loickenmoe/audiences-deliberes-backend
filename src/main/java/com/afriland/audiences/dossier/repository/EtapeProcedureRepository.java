package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.TypeEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EtapeProcedureRepository extends JpaRepository<EtapeProcedure, Long> {

    List<EtapeProcedure> findByDossierIdOrderByTypeAsc(Long dossierId);

    Optional<EtapeProcedure> findByDossierIdAndType(Long dossierId, TypeEtape type);

    boolean existsByDossierIdAndType(Long dossierId, TypeEtape type);
}
