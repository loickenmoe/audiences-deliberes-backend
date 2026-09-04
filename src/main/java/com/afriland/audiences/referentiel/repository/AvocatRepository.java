package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Avocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AvocatRepository extends JpaRepository<Avocat, Long> {

    Optional<Avocat> findByCompteKeycloak(String compteKeycloak);
}
