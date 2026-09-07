package com.afriland.audiences.frais.repository;

import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValidationFraisRepository extends JpaRepository<ValidationFrais, Long> {

    Optional<ValidationFrais> findByDemandeFraisIdAndEtape(Long demandeFraisId, EtapeValidationFrais etape);

    boolean existsByDemandeFraisIdAndEtapeAndDecision(Long demandeFraisId, EtapeValidationFrais etape, DecisionValidationFrais decision);
}
