package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Avocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvocatRepository extends JpaRepository<Avocat, Long> {

    Optional<Avocat> findByCompteKeycloak(String compteKeycloak);

    /**
     * Registrations of several accounts at once.
     *
     * <p>The lawyer activity report counts per account, then names each from the directory. One read
     * per row would issue as many queries as active lawyers in the period.
     */
    List<Avocat> findByCompteKeycloakIn(java.util.Collection<String> comptes);

    /** Search by name (FR-INT-08/US 5.8, `GET /repertoire/avocats?nom=`). */
    List<Avocat> findByNomContainingIgnoreCase(String nom);
}
