package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Q-84 : lien entre un compte applicatif et un avocat du référentiel.
 * <p>
 * {@code avocat.compte_keycloak} est ce que le DJ saisit à la création de l'avocat : son
 * <b>identifiant de connexion</b> ({@code preferred_username}). Jusqu'à Q-84, le backend le
 * comparait au {@code sub} du jeton — un UUID que personne ne connaît : aucun avocat saisi par
 * l'interface n'était reconnu. Le {@code sub} reste accepté en second, pour les comptes déjà
 * rattachés ainsi.
 */
@Service
@RequiredArgsConstructor
public class CompteAvocatService {

    private final AvocatRepository avocatRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** L'avocat de cet utilisateur : par identifiant de connexion, à défaut par {@code sub}. */
    public Optional<Avocat> avocatDe(Utilisateur utilisateur) {
        Optional<Avocat> parIdentifiant = StringUtils.hasText(utilisateur.getIdentifiant())
                ? avocatRepository.findByCompteKeycloak(utilisateur.getIdentifiant())
                : Optional.empty();
        return parIdentifiant.or(() -> avocatRepository.findByCompteKeycloak(utilisateur.getKeycloakId()));
    }

    /** Le compte applicatif d'un avocat, destinataire de ses alertes — même double lecture. */
    public Optional<Utilisateur> utilisateurDe(Avocat avocat) {
        String compte = avocat.getCompteKeycloak();
        return utilisateurRepository.findByIdentifiant(compte)
                .or(() -> utilisateurRepository.findByKeycloakId(compte));
    }

    /** Vrai si cet utilisateur est bien cet avocat. */
    public boolean estLeCompteDe(Utilisateur utilisateur, Avocat avocat) {
        return avocatDe(utilisateur).map(a -> a.getId().equals(avocat.getId())).orElse(false);
    }
}
