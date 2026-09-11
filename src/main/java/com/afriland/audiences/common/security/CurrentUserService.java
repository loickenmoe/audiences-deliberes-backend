package com.afriland.audiences.common.security;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Résout l'utilisateur authentifié à partir du jeton Keycloak, en le provisionnant dans la table
 * {@code utilisateur} au premier appel (aucun compte n'est créé côté Keycloak par l'application —
 * REF-05 §1.3 : le backend n'implémente aucune gestion de credentials, seule la synchronisation
 * du profil applicatif est de son ressort).
 * <p>
 * Le rôle « métier » principal (un des 6 profils de saisie) est déterminé à partir du claim
 * {@code roles} du jeton ; les rôles transverses {@code ROLE_SAISIE} et {@code ROLE_CONSULTATION}
 * ne sont jamais retenus comme profil (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-08).
 * <p>
 * <b>La création elle-même est déléguée à {@link UtilisateurProvisioningService}</b>, qui l'exécute
 * dans une transaction en écriture indépendante — sans quoi un appelant en lecture seule ferait
 * échouer l'INSERT (R-06, corrigé en M15). La lecture reste, elle, compatible avec un contexte en
 * lecture seule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurProvisioningService provisioningService;

    /**
     * Retourne l'utilisateur correspondant au jeton de la requête courante, en le créant si
     * c'est sa première requête authentifiée.
     */
    @Transactional(readOnly = true)
    public Utilisateur getCurrentUser() {
        return resolveOrProvision(extractJwt());
    }

    @Transactional(readOnly = true)
    public Utilisateur resolveOrProvision(Jwt jwt) {
        return utilisateurRepository.findByKeycloakId(jwt.getSubject())
                .map(utilisateur -> synchroniserIdentifiant(utilisateur, jwt))
                .orElseGet(() -> provisioningService.provisionner(jwt));
    }

    /**
     * Q-84 : l'identifiant de connexion sert à reconnaître un avocat. Un utilisateur provisionné
     * avant Q-84, ou renommé dans Keycloak, le reçoit à sa requête suivante — écrit par le service
     * de provisionnement, dans sa propre transaction (R-06).
     */
    private Utilisateur synchroniserIdentifiant(Utilisateur utilisateur, Jwt jwt) {
        String identifiant = jwt.getClaimAsString(UtilisateurProvisioningService.CLAIM_IDENTIFIANT);
        if (identifiant != null && !identifiant.equals(utilisateur.getIdentifiant())) {
            provisioningService.synchroniserIdentifiant(utilisateur.getId(), identifiant);
            utilisateur.setIdentifiant(identifiant);
        }
        return utilisateur;
    }

    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.NON_AUTHENTIFIE,
                    "Aucun utilisateur authentifié dans le contexte courant");
        }
        return jwtAuthentication.getToken();
    }
}
