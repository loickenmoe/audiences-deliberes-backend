package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandeConstitutionRepository extends JpaRepository<DemandeConstitution, Long> {
}
