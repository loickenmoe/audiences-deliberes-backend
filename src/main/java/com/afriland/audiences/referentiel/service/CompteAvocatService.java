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
 * Link between an application account and a lawyer in the reference list.
 * <p>
 * {@code avocat.compte_keycloak} is what the DJ enters when creating the lawyer: their <b>login
 * identifier</b> ({@code preferred_username}). The token {@code sub} is still accepted as a
 * fallback, for accounts linked that way.
 */
@Service
@RequiredArgsConstructor
public class CompteAvocatService {

    private final AvocatRepository avocatRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** The lawyer of this user: by login identifier, otherwise by {@code sub}. */
    public Optional<Avocat> avocatDe(Utilisateur utilisateur) {
        Optional<Avocat> parIdentifiant = StringUtils.hasText(utilisateur.getIdentifiant())
                ? avocatRepository.findByCompteKeycloak(utilisateur.getIdentifiant())
                : Optional.empty();
        return parIdentifiant.or(() -> avocatRepository.findByCompteKeycloak(utilisateur.getKeycloakId()));
    }

    /** A lawyer's application account, recipient of their alerts; same dual lookup. */
    public Optional<Utilisateur> utilisateurDe(Avocat avocat) {
        String compte = avocat.getCompteKeycloak();
        return utilisateurRepository.findByIdentifiant(compte)
                .or(() -> utilisateurRepository.findByKeycloakId(compte));
    }

    /** True if this user is this lawyer. */
    public boolean estLeCompteDe(Utilisateur utilisateur, Avocat avocat) {
        return avocatDe(utilisateur).map(a -> a.getId().equals(avocat.getId())).orElse(false);
    }
}
