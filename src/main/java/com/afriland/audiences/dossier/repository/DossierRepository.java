package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.Dossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long>, JpaSpecificationExecutor<Dossier> {

    Optional<Dossier> findByReference(String reference);

    boolean existsByReference(String reference);

    List<Dossier> findByClientId(Long clientId);
}
