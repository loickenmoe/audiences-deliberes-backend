package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Intervenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Résolution polymorphe d'un prestataire (avocat ou autre) — {@code demande_constitution.prestataire_id} (RG-INT-08, M9). */
@Repository
public interface IntervenantRepository extends JpaRepository<Intervenant, Long> {
}
