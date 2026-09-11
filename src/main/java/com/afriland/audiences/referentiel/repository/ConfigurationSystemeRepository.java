package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigurationSystemeRepository extends JpaRepository<ConfigurationSysteme, Long> {

    Optional<ConfigurationSysteme> findByCle(String cle);
}
