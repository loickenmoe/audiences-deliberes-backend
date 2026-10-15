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
     * Les inscriptions de plusieurs comptes d'un coup (Q-136).
     *
     * <p>Le rapport d'activite des avocats compte d'abord par compte, puis doit nommer chacun
     * depuis le repertoire. Une lecture par ligne ferait autant de requetes que d'avocats actifs
     * sur la periode, pour un tableau qu'on affiche d'un bloc.
     */
    List<Avocat> findByCompteKeycloakIn(java.util.Collection<String> comptes);

    /** Recherche par nom (FR-INT-08/US 5.8, `GET /repertoire/avocats?nom=`). */
    List<Avocat> findByNomContainingIgnoreCase(String nom);
}
