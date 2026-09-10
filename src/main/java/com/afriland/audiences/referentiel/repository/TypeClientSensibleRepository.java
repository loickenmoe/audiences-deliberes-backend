package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.TypeClientSensible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TypeClientSensibleRepository extends JpaRepository<TypeClientSensible, Long> {

    List<TypeClientSensible> findByActifTrue();

    /** Contrôle d'appartenance au référentiel (Q-73) : {@code dossier.type_client_sensible} porte le code. */
    Optional<TypeClientSensible> findByCodeIgnoreCase(String code);
}
