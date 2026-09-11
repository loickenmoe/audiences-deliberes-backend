package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompteAvocatServiceTest {

    @Mock private AvocatRepository avocatRepository;
    @Mock private UtilisateurRepository utilisateurRepository;

    private CompteAvocatService service;
    private final Avocat avocat = Avocat.builder().id(5L).nom("Me Fotso").compteKeycloak("avocat.fotso").build();

    @BeforeEach
    void setUp() {
        service = new CompteAvocatService(avocatRepository, utilisateurRepository);
    }

    @Test
    void avocatDe_recognisesTheLogin() {
        Utilisateur utilisateur = Utilisateur.builder().id(1L).keycloakId("sub-1").identifiant("avocat.fotso").build();
        when(avocatRepository.findByCompteKeycloak("avocat.fotso")).thenReturn(Optional.of(avocat));

        assertThat(service.avocatDe(utilisateur)).contains(avocat);
        verify(avocatRepository, never()).findByCompteKeycloak("sub-1");
    }

    @Test
    void avocatDe_fallsBackToTheSubjectForAccountsLinkedBeforeQ84() {
        Utilisateur utilisateur = Utilisateur.builder().id(1L).keycloakId("sub-1").identifiant("avocat.fotso").build();
        when(avocatRepository.findByCompteKeycloak("avocat.fotso")).thenReturn(Optional.empty());
        when(avocatRepository.findByCompteKeycloak("sub-1")).thenReturn(Optional.of(avocat));

        assertThat(service.avocatDe(utilisateur)).contains(avocat);
    }

    @Test
    void avocatDe_withoutLoginUsesTheSubjectOnly() {
        Utilisateur utilisateur = Utilisateur.builder().id(1L).keycloakId("sub-1").build();
        when(avocatRepository.findByCompteKeycloak("sub-1")).thenReturn(Optional.empty());

        assertThat(service.avocatDe(utilisateur)).isEmpty();
    }

    @Test
    void utilisateurDe_findsTheAccountByLoginThenBySubject() {
        Utilisateur parIdentifiant = Utilisateur.builder().id(1L).identifiant("avocat.fotso").build();
        when(utilisateurRepository.findByIdentifiant("avocat.fotso")).thenReturn(Optional.of(parIdentifiant));
        assertThat(service.utilisateurDe(avocat)).contains(parIdentifiant);

        Avocat ancien = Avocat.builder().id(6L).nom("Me Ancien").compteKeycloak("sub-2").build();
        Utilisateur parSub = Utilisateur.builder().id(2L).keycloakId("sub-2").build();
        when(utilisateurRepository.findByIdentifiant("sub-2")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByKeycloakId("sub-2")).thenReturn(Optional.of(parSub));
        assertThat(service.utilisateurDe(ancien)).contains(parSub);
    }

    @Test
    void estLeCompteDe_comparesTheResolvedLawyer() {
        Utilisateur utilisateur = Utilisateur.builder().id(1L).keycloakId("sub-1").identifiant("avocat.fotso").build();
        when(avocatRepository.findByCompteKeycloak("avocat.fotso")).thenReturn(Optional.of(avocat));

        assertThat(service.estLeCompteDe(utilisateur, avocat)).isTrue();
        assertThat(service.estLeCompteDe(utilisateur, Avocat.builder().id(9L).build())).isFalse();
    }
}
