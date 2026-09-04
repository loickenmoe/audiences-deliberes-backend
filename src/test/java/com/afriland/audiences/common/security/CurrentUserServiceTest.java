package com.afriland.audiences.common.security;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private ProfilRepository profilRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveOrProvision_returnsExistingUserWithoutCreatingANewOne() {
        Jwt jwt = jwtWithRoles("kc-123", List.of("ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION"));
        Utilisateur existing = Utilisateur.builder().id(42L).keycloakId("kc-123").build();
        when(utilisateurRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(existing));

        Utilisateur result = currentUserService.resolveOrProvision(jwt);

        assertThat(result).isSameAs(existing);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void resolveOrProvision_provisionsNewUserWithPrimaryProfilRoleOnFirstRequest() {
        Jwt jwt = jwtWithRoles("kc-456", List.of("ROLE_DJ", "ROLE_SAISIE", "ROLE_CONSULTATION"));
        Profil djProfil = Profil.builder().id(2L).code("DJ").libelle("Directeur Juridique").build();
        when(utilisateurRepository.findByKeycloakId("kc-456")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("DJ")).thenReturn(Optional.of(djProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur result = currentUserService.resolveOrProvision(jwt);

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        Utilisateur saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo("kc-456");
        assertThat(saved.getProfil()).isEqualTo(djProfil);
        assertThat(saved.getActif()).isTrue();
        assertThat(result.getProfil().getCode()).isEqualTo("DJ");
    }

    @Test
    void resolveOrProvision_ignoresTransverseRolesWhenPickingTheProfil() {
        // ROLE_SAISIE et ROLE_CONSULTATION apparaissent avant ROLE_ASSISTANTE dans le jeton :
        // seul le rôle métier doit être retenu comme profil (cf. Q-08).
        Jwt jwt = jwtWithRoles("kc-789", List.of("ROLE_SAISIE", "ROLE_CONSULTATION", "ROLE_ASSISTANTE"));
        Profil assistanteProfil = Profil.builder().id(4L).code("ASSISTANTE").build();
        when(utilisateurRepository.findByKeycloakId("kc-789")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("ASSISTANTE")).thenReturn(Optional.of(assistanteProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur result = currentUserService.resolveOrProvision(jwt);

        assertThat(result.getProfil().getCode()).isEqualTo("ASSISTANTE");
    }

    @Test
    void resolveOrProvision_rejectsTokenWithNoRecognizedBusinessRole() {
        Jwt jwt = jwtWithRoles("kc-000", List.of("ROLE_SAISIE", "ROLE_CONSULTATION"));
        when(utilisateurRepository.findByKeycloakId("kc-000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.resolveOrProvision(jwt))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucun rôle métier reconnu");
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
