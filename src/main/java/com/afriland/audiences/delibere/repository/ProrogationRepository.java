package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Prorogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProrogationRepository extends JpaRepository<Prorogation, Long> {

    long countByDelibereId(Long delibereId);
}
