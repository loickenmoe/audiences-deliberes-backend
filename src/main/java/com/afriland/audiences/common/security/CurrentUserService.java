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
 * Resolves the authenticated user from the Keycloak token, provisioning it in the {@code utilisateur}
 * table on first call. The application never creates Keycloak accounts nor manages credentials
 * (REF-05 §1.3).
 * <p>
 * The main business profile is derived from the token's {@code roles} claim; the cross-cutting roles
 * {@code ROLE_SAISIE} and {@code ROLE_CONSULTATION} are never used as a profile.
 * <p>
 * Creation is delegated to {@link UtilisateurProvisioningService}, which runs in its own write
 * transaction so that read-only callers still work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurProvisioningService provisioningService;

    /** Returns the user of the current request's token, creating it on its first authenticated request. */
    @Transactional(readOnly = true)
    public Utilisateur getCurrentUser() {
        return resoudreOuProvisionner(extractJwt());
    }

    @Transactional(readOnly = true)
    public Utilisateur resolveOrProvision(Jwt jwt) {
        return resoudreOuProvisionner(jwt);
    }

    private Utilisateur resoudreOuProvisionner(Jwt jwt) {
        return utilisateurRepository.findByKeycloakId(jwt.getSubject())
                .map(utilisateur -> synchroniserIdentifiant(utilisateur, jwt))
                .orElseGet(() -> provisioningService.provisionner(jwt));
    }

    /**
     * The login identifier is used to recognize a lawyer. A user provisioned without it, or renamed in
     * Keycloak, gets it on the next request (written in the provisioning service's own transaction).
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
