package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByKeycloakId(String keycloakId);

    Optional<Utilisateur> findByEmail(String email);

    /** Utilisateurs actifs portant un profil donné (ex. "DJ") — destinataires des alertes de supervision (M5). */
    List<Utilisateur> findByProfilCodeAndActifTrue(String profilCode);
}
