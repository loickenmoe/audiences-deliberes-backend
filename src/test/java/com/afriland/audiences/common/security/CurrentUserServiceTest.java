package com.afriland.audiences.common.security;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Depuis M15, ce service ne fait plus que <b>résoudre</b> l'utilisateur : la création est déléguée
 * à {@link UtilisateurProvisioningService} pour qu'elle s'exécute dans sa propre transaction en
 * écriture (correctif R-06). Les règles de dérivation du profil sont donc testées dans
 * {@link UtilisateurProvisioningServiceTest} ; ici, seule la délégation est vérifiée.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private UtilisateurProvisioningService provisioningService;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveOrProvision_returnsExistingUserWithoutProvisioning() {
        Jwt jwt = jwtWithRoles("kc-123", List.of("ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION"));
        Utilisateur existing = Utilisateur.builder().id(42L).keycloakId("kc-123").build();
        when(utilisateurRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(existing));

        Utilisateur result = currentUserService.resolveOrProvision(jwt);

        assertThat(result).isSameAs(existing);
        verify(provisioningService, never()).provisionner(any());
    }

    @Test
    void resolveOrProvision_delegatesToTheProvisioningServiceOnFirstRequest() {
        Jwt jwt = jwtWithRoles("kc-456", List.of("ROLE_DJ", "ROLE_SAISIE"));
        Utilisateur provisionne = Utilisateur.builder().id(7L).keycloakId("kc-456").build();
        when(utilisateurRepository.findByKeycloakId("kc-456")).thenReturn(Optional.empty());
        when(provisioningService.provisionner(jwt)).thenReturn(provisionne);

        assertThat(currentUserService.resolveOrProvision(jwt)).isSameAs(provisionne);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void getCurrentUser_readsTheJwtFromTheSecurityContext() {
        Jwt jwt = jwtWithRoles("kc-111", List.of("ROLE_AVOCAT"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Utilisateur existing = Utilisateur.builder().id(1L).keycloakId("kc-111").build();
        when(utilisateurRepository.findByKeycloakId("kc-111")).thenReturn(Optional.of(existing));

        Utilisateur result = currentUserService.getCurrentUser();

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getCurrentUser_rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "creds"));

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(ApiException.class);
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(subject)
                .claim("roles", roles)
                .claim("given_name", "Test")
                .claim("family_name", "User")
                .claim("email", subject + "@afriland.cm")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
