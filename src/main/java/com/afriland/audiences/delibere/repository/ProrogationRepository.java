package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Prorogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProrogationRepository extends JpaRepository<Prorogation, Long> {

    long countByDelibereId(Long delibereId);

    /** Q-78, FR-DEL-04 critère 2 : historique des prorogations, dans l'ordre où elles ont été prononcées. */
    List<Prorogation> findByDelibereIdOrderByCompteurAsc(Long delibereId);
}
