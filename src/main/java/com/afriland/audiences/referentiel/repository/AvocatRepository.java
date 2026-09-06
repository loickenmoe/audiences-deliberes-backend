package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Avocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvocatRepository extends JpaRepository<Avocat, Long> {

    Optional<Avocat> findByCompteKeycloak(String compteKeycloak);

    /** Recherche par nom (FR-INT-08/US 5.8, `GET /repertoire/avocats?nom=`). */
    List<Avocat> findByNomContainingIgnoreCase(String nom);
}
