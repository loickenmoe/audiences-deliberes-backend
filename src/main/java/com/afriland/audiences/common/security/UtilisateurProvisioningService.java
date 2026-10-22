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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Provisions a {@link Utilisateur} from the Keycloak token in its own write transaction
 * ({@link Propagation#REQUIRES_NEW}). Otherwise a read-only caller's transaction would make
 * PostgreSQL reject the INSERT, failing a new user's first request with a 500.
 * <p>
 * A separate bean is required: {@code @Transactional} is proxy-based, so an internal call within
 * {@code CurrentUserService} would not start a new transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtilisateurProvisioningService {

    /**
     * Business profiles by priority. DJ and DJA also carry {@code ROLE_JURISTE} (composite realm role)
     * and Keycloak does not guarantee role order, so the profile is chosen by priority. Same order as
     * {@code profilPrincipal} on the frontend.
     */
    private static final List<String> PRIORITE_PROFILS = List.of(
            "DJ", "DJA", "SH", "ASSISTANTE", "JURISTE", "AVOCAT");

    /** Keycloak claim holding the login identifier. */
    static final String CLAIM_IDENTIFIANT = "preferred_username";

    private final UtilisateurRepository utilisateurRepository;
    private final ProfilRepository profilRepository;

    /**
     * Creates the user if missing, in a transaction independent from the caller's. Existence is
     * re-checked inside the new transaction so two concurrent first requests do not create two rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Utilisateur provisionner(Jwt jwt) {
        return utilisateurRepository.findByKeycloakId(jwt.getSubject())
                .orElseGet(() -> creer(jwt));
    }

    /**
     * Stores the login identifier of an already provisioned user (missing or renamed in Keycloak), in
     * a separate transaction for the same reason as creation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchroniserIdentifiant(Long utilisateurId, String identifiant) {
        utilisateurRepository.findById(utilisateurId).ifPresent(utilisateur -> {
            utilisateur.setIdentifiant(identifiant);
            utilisateurRepository.save(utilisateur);
        });
    }

    private Utilisateur creer(Jwt jwt) {
        Profil profil = resolveProfil(jwt);
        Utilisateur saved = utilisateurRepository.save(Utilisateur.builder()
                .keycloakId(jwt.getSubject())
                .identifiant(jwt.getClaimAsString(CLAIM_IDENTIFIANT))
                .nom(jwt.getClaimAsString("family_name"))
                .prenom(jwt.getClaimAsString("given_name"))
                .email(jwt.getClaimAsString("email"))
                .profil(profil)
                .actif(true)
                .build());
        log.info("Utilisateur provisionné : keycloakId={}, profil={}", jwt.getSubject(), profil.getCode());
        return saved;
    }

    private Profil resolveProfil(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = List.of();
        }
        List<String> codes = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .toList();
        String code = PRIORITE_PROFILS.stream()
                .filter(codes::contains)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.DROITS_INSUFFISANTS,
                        "Aucun rôle métier reconnu dans le jeton Keycloak"));
        return profilRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.ERREUR_INTERNE,
                        "Profil référentiel introuvable pour le code : " + code));
    }
}
