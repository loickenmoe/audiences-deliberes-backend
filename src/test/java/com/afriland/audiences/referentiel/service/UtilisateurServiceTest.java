package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.web.dto.UtilisateurResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** M15 (Q-69) — répertoire des utilisateurs internes : filtres et tri. */
@ExtendWith(MockitoExtension.class)
class UtilisateurServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;

    private UtilisateurService service;

    @BeforeEach
    void setUp() {
        service = new UtilisateurService(utilisateurRepository);
        when(utilisateurRepository.findAll()).thenReturn(List.of(
                utilisateur(1L, "Ndjock", "Alice", "JURISTE", true),
                utilisateur(2L, "Bello", "Marc", "JURISTE", true),
                utilisateur(3L, "Kamga", "Sonia", "DJ", true),
                utilisateur(4L, "Ancien", "Paul", "JURISTE", false)));
    }

    @Test
    void lister_sansFiltre_neRetourneQueLesComptesActifs() {
        List<UtilisateurResponse> result = service.lister(null, false);

        assertThat(result).hasSize(3);
        assertThat(result).noneMatch(u -> Boolean.FALSE.equals(u.actif()));
    }

    @Test
    void lister_trieParNomPuisPrenom() {
        assertThat(service.lister(null, false))
                .extracting(UtilisateurResponse::nom)
                .containsExactly("Bello", "Kamga", "Ndjock");
    }

    @Test
    void lister_filtreParProfil() {
        assertThat(service.lister("JURISTE", false))
                .extracting(UtilisateurResponse::id)
                .containsExactly(2L, 1L);
    }

    /** Le code de profil vient d'une URL : la casse ne doit pas faire échouer le filtre. */
    @Test
    void lister_filtreParProfil_insensibleALaCasse() {
        assertThat(service.lister("juriste", false)).hasSize(2);
    }

    @Test
    void lister_peutInclureLesComptesDesactives() {
        assertThat(service.lister("JURISTE", true))
                .extracting(UtilisateurResponse::nom)
                .containsExactly("Ancien", "Bello", "Ndjock");
    }

    /** {@code keycloakId} est un identifiant d'authentification : il ne doit pas fuiter. */
    @Test
    void lister_exposeLesChampsUtilesSansIdentifiantDAuthentification() {
        UtilisateurResponse premier = service.lister("DJ", false).getFirst();

        assertThat(premier.id()).isEqualTo(3L);
        assertThat(premier.nom()).isEqualTo("Kamga");
        assertThat(premier.prenom()).isEqualTo("Sonia");
        assertThat(premier.email()).isEqualTo("kamga@afriland.cm");
        assertThat(premier.profil()).isEqualTo("DJ");
        assertThat(UtilisateurResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("keycloakId");
    }

    private Utilisateur utilisateur(Long id, String nom, String prenom, String profil, boolean actif) {
        return Utilisateur.builder()
                .id(id)
                .keycloakId("kc-" + id)
                .nom(nom)
                .prenom(prenom)
                .email(nom.toLowerCase() + "@afriland.cm")
                .profil(Profil.builder().code(profil).build())
                .actif(actif)
                .build();
    }
}
