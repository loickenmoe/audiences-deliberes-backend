package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.LettreConstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LettreConstitutionRepository extends JpaRepository<LettreConstitution, Long> {

    Optional<LettreConstitution> findByDemandeId(Long demandeId);

    boolean existsByNumero(Integer numero);

    /**
     * Next number proposed by the sequence. A sequence rather than {@code max(numero) + 1}, so that
     * concurrent validations never read the same maximum. The number stays editable; the database
     * uniqueness constraint is what prevents duplicates.
     */
    @Query(value = "SELECT nextval('lettre_constitution_numero_seq')", nativeQuery = true)
    long prochainNumero();
}
