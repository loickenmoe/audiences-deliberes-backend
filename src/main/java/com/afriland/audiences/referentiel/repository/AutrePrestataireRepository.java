package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutrePrestataireRepository extends JpaRepository<AutrePrestataire, Long> {
}
