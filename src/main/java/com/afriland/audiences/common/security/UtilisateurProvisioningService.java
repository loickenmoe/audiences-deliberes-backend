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
 * Provisionne un {@link Utilisateur} à partir du jeton Keycloak, <b>dans sa propre transaction en
 * écriture</b> (correctif R-06, M15).
 * <p>
 * Ce service existe uniquement pour porter {@link Propagation#REQUIRES_NEW} : l'INSERT de
 * provisionnement était auparavant exécuté dans {@code CurrentUserService}, donc dans la
 * transaction de l'appelant. Tout endpoint en lecture seule
 * ({@code @Transactional(readOnly = true)}) propageait son mode à cet INSERT, que PostgreSQL
 * rejetait — « cannot execute INSERT in a read-only transaction » — faisant échouer en 500 la
 * **première requête de tout nouvel utilisateur** dès lors qu'elle tombait sur une lecture
 * (typiquement `GET /alertes/mes-notifications` ou `GET /dossiers` au chargement du frontend).
 * <p>
 * Une classe distincte est nécessaire : {@code @Transactional} s'appuie sur un proxy, un appel
 * interne à {@code CurrentUserService} n'aurait donc pas changé de transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtilisateurProvisioningService {

    private static final Set<String> PROFIL_ROLES = Set.of(
            "JURISTE", "DJ", "DJA", "ASSISTANTE", "SH", "AVOCAT");

    private final UtilisateurRepository utilisateurRepository;
    private final ProfilRepository profilRepository;

    /**
     * Crée l'utilisateur s'il n'existe pas encore, dans une transaction indépendante de celle de
     * l'appelant. Reteste l'existence à l'intérieur de la nouvelle transaction : deux requêtes
     * concurrentes du même nouvel utilisateur ne doivent pas produire deux lignes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Utilisateur provisionner(Jwt jwt) {
        return utilisateurRepository.findByKeycloakId(jwt.getSubject())
                .orElseGet(() -> creer(jwt));
    }

    private Utilisateur creer(Jwt jwt) {
        Profil profil = resolveProfil(jwt);
        Utilisateur saved = utilisateurRepository.save(Utilisateur.builder()
                .keycloakId(jwt.getSubject())
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
