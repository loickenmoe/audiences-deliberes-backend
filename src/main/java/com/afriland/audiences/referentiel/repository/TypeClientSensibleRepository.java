package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.TypeClientSensible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TypeClientSensibleRepository extends JpaRepository<TypeClientSensible, Long> {

    List<TypeClientSensible> findByActifTrue();
}
