package com.afriland.audiences.common.security;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

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
 * M15 — règles de dérivation du profil applicatif à partir du jeton Keycloak, déplacées ici depuis
 * {@code CurrentUserServiceTest} avec le code qu'elles couvrent (correctif R-06). Le comportement
 * transactionnel lui-même ({@code REQUIRES_NEW}) ne peut pas être vérifié sur des mocks : il l'est
 * par {@code ProvisionnementLectureSeuleIntegrationTest}, contre PostgreSQL réel.
 */
@ExtendWith(MockitoExtension.class)
class UtilisateurProvisioningServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private ProfilRepository profilRepository;

    @InjectMocks
    private UtilisateurProvisioningService provisioningService;

    @Test
    void provisionner_createsTheUserWithItsPrimaryProfilRole() {
        Jwt jwt = jwtWithRoles("kc-456", List.of("ROLE_DJ", "ROLE_SAISIE", "ROLE_CONSULTATION"));
        Profil djProfil = Profil.builder().id(2L).code("DJ").libelle("Directeur Juridique").build();
        when(utilisateurRepository.findByKeycloakId("kc-456")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("DJ")).thenReturn(Optional.of(djProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur result = provisioningService.provisionner(jwt);

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        Utilisateur saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo("kc-456");
        assertThat(saved.getProfil()).isEqualTo(djProfil);
        assertThat(saved.getActif()).isTrue();
        assertThat(result.getProfil().getCode()).isEqualTo("DJ");
    }

    @Test
    void provisionner_ignoresTransverseRolesWhenPickingTheProfil() {
        // ROLE_SAISIE et ROLE_CONSULTATION apparaissent avant ROLE_ASSISTANTE dans le jeton :
        // seul le rôle métier doit être retenu comme profil (cf. Q-08).
        Jwt jwt = jwtWithRoles("kc-789", List.of("ROLE_SAISIE", "ROLE_CONSULTATION", "ROLE_ASSISTANTE"));
        Profil assistanteProfil = Profil.builder().id(4L).code("ASSISTANTE").build();
        when(utilisateurRepository.findByKeycloakId("kc-789")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("ASSISTANTE")).thenReturn(Optional.of(assistanteProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(provisioningService.provisionner(jwt).getProfil().getCode()).isEqualTo("ASSISTANTE");
    }

    /**
     * Q-81 : le DJ et la DJA portent aussi {@code ROLE_JURISTE} (rôle composite), et l'ordre des
     * rôles dans le jeton n'est pas garanti. Le profil se choisit par priorité : un nouveau DJ ne
     * doit jamais être enregistré comme juriste.
     */
    @Test
    void provisionner_prefereLeProfilDjAuRoleJuristeComposite() {
        Jwt jwt = jwtWithRoles("kc-dj-q81", List.of("ROLE_JURISTE", "ROLE_SAISIE", "ROLE_DJ", "ROLE_CONSULTATION"));
        Profil djProfil = Profil.builder().id(2L).code("DJ").build();
        when(utilisateurRepository.findByKeycloakId("kc-dj-q81")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("DJ")).thenReturn(Optional.of(djProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(provisioningService.provisionner(jwt).getProfil().getCode()).isEqualTo("DJ");
    }

    @Test
    void provisionner_prefereLeProfilDjaAuRoleJuristeComposite() {
        Jwt jwt = jwtWithRoles("kc-dja-q81", List.of("ROLE_JURISTE", "ROLE_DJA", "ROLE_SAISIE"));
        Profil djaProfil = Profil.builder().id(3L).code("DJA").build();
        when(utilisateurRepository.findByKeycloakId("kc-dja-q81")).thenReturn(Optional.empty());
        when(profilRepository.findByCode("DJA")).thenReturn(Optional.of(djaProfil));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(provisioningService.provisionner(jwt).getProfil().getCode()).isEqualTo("DJA");
    }

    @Test
    void provisionner_rejectsTokenWithNoRecognizedBusinessRole() {
        Jwt jwt = jwtWithRoles("kc-000", List.of("ROLE_SAISIE", "ROLE_CONSULTATION"));
        when(utilisateurRepository.findByKeycloakId("kc-000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.provisionner(jwt))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucun rôle métier reconnu");
        verify(utilisateurRepository, never()).save(any());
    }

    /**
     * Deux requêtes concurrentes du même nouvel utilisateur ne doivent pas produire deux lignes :
     * l'existence est retestée à l'intérieur de la transaction indépendante.
     */
    @Test
    void provisionner_returnsTheExistingUserIfItAppearedMeanwhile() {
        Jwt jwt = jwtWithRoles("kc-race", List.of("ROLE_JURISTE"));
        Utilisateur deja = Utilisateur.builder().id(3L).keycloakId("kc-race").build();
        when(utilisateurRepository.findByKeycloakId("kc-race")).thenReturn(Optional.of(deja));

        assertThat(provisioningService.provisionner(jwt)).isSameAs(deja);
        verify(utilisateurRepository, never()).save(any());
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
