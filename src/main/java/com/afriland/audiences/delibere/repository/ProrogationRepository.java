package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Prorogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProrogationRepository extends JpaRepository<Prorogation, Long> {

    long countByDelibereId(Long delibereId);

    /** FR-DEL-04 criterion 2: extension history, in the order the extensions were pronounced. */
    List<Prorogation> findByDelibereIdOrderByCompteurAsc(Long delibereId);
}
