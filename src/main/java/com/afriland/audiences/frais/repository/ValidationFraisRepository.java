package com.afriland.audiences.frais.repository;

import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationFraisRepository extends JpaRepository<ValidationFrais, Long> {

    Optional<ValidationFrais> findByDemandeFraisIdAndEtape(Long demandeFraisId, EtapeValidationFrais etape);

    boolean existsByDemandeFraisIdAndEtapeAndDecision(Long demandeFraisId, EtapeValidationFrais etape, DecisionValidationFrais decision);

    /** Q-82 : décisions d'une demande, dans l'ordre du circuit. */
    List<ValidationFrais> findByDemandeFraisIdOrderByDateValidationAscIdAsc(Long demandeFraisId);

    /** Q-82 : décisions d'une page de demandes en une requête (file de travail). */
    List<ValidationFrais> findByDemandeFraisIdInOrderByDateValidationAscIdAsc(Collection<Long> demandeFraisIds);
}
