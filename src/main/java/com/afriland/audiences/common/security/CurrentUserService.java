package com.afriland.audiences.common.security;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
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

import java.util.List;
import java.util.Set;

/**
 * Résout l'utilisateur authentifié à partir du jeton Keycloak, en le provisionnant dans la table
 * {@code utilisateur} au premier appel (aucun compte n'est créé côté Keycloak par l'application —
 * REF-05 §1.3 : le backend n'implémente aucune gestion de credentials, seule la synchronisation
 * du profil applicatif est de son ressort).
 * <p>
 * Le rôle « métier » principal (un des 6 profils de saisie) est déterminé à partir du claim
 * {@code roles} du jeton ; les rôles transverses {@code ROLE_SAISIE} et {@code ROLE_CONSULTATION}
 * ne sont jamais retenus comme profil (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final Set<String> PROFIL_ROLES = Set.of(
            "JURISTE", "DJ", "DJA", "ASSISTANTE", "SH", "AVOCAT");

    private final UtilisateurRepository utilisateurRepository;
    private final ProfilRepository profilRepository;

    /**
     * Retourne l'utilisateur correspondant au jeton de la requête courante, en le créant si
     * c'est sa première requête authentifiée.
     */
    @Transactional
    public Utilisateur getCurrentUser() {
        return resolveOrProvision(extractJwt());
    }

    @Transactional
    public Utilisateur resolveOrProvision(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return utilisateurRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> provisionFromJwt(jwt));
    }

    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.NON_AUTHENTIFIE,
                    "Aucun utilisateur authentifié dans le contexte courant");
        }
        return jwtAuthentication.getToken();
    }

    private Utilisateur provisionFromJwt(Jwt jwt) {
        Profil profil = resolveProfil(jwt);
        Utilisateur utilisateur = Utilisateur.builder()
                .keycloakId(jwt.getSubject())
                .nom(jwt.getClaimAsString("family_name"))
                .prenom(jwt.getClaimAsString("given_name"))
                .email(jwt.getClaimAsString("email"))
                .profil(profil)
                .actif(true)
                .build();
        Utilisateur saved = utilisateurRepository.save(utilisateur);
        log.info("Utilisateur provisionné : keycloakId={}, profil={}", jwt.getSubject(), profil.getCode());
        return saved;
    }

    private Profil resolveProfil(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = List.of();
        }
        String code = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .filter(PROFIL_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.DROITS_INSUFFISANTS,
                        "Aucun rôle métier reconnu dans le jeton Keycloak"));
        return profilRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.ERREUR_INTERNE,
                        "Profil référentiel introuvable pour le code : " + code));
    }
}
