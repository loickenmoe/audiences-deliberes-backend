package com.afriland.audiences.decision.repository;

import com.afriland.audiences.decision.domain.DecisionDefinitive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DecisionDefinitiveRepository extends JpaRepository<DecisionDefinitive, Long> {
}
