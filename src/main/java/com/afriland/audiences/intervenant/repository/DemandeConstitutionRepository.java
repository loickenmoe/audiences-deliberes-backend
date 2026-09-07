package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeConstitutionRepository extends JpaRepository<DemandeConstitution, Long> {

    /** M15 (Q-68) : file de travail « constitutions à signer » du SH, filtrable par statut. */
    List<DemandeConstitution> findByStatutOrderByDateDemandeAsc(StatutConstitution statut);

    List<DemandeConstitution> findByOrderByDateDemandeDesc();
}
