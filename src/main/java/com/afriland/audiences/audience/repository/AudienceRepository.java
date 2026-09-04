package com.afriland.audiences.audience.repository;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AudienceRepository extends JpaRepository<Audience, Long> {

    boolean existsByEtapeIdAndDatePlanifiee(Long etapeId, LocalDate datePlanifiee);

    List<Audience> findByDatePlanifieeBetween(LocalDate debut, LocalDate fin);

    /** RG-AUD-03 (rappels J-7/J-3/J-1) : audiences encore planifiées à une date donnée. */
    List<Audience> findByStatutAndDatePlanifiee(StatutAudience statut, LocalDate datePlanifiee);
}
