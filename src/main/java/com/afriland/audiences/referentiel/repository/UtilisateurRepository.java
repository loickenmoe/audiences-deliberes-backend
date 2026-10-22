package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByKeycloakId(String keycloakId);

    /** By login identifier ({@code preferred_username}). */
    Optional<Utilisateur> findByIdentifiant(String identifiant);

    Optional<Utilisateur> findByEmail(String email);

    /** Active users with a given profile (e.g. "DJ"), recipients of supervision alerts. */
    List<Utilisateur> findByProfilCodeAndActifTrue(String profilCode);
}
