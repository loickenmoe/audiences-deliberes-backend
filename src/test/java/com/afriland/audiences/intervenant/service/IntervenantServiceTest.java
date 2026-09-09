package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M15 (Q-68) — création d'un intervenant : les invariants RG-INT-01/02 sont portés par le service. */
@ExtendWith(MockitoExtension.class)
class IntervenantServiceTest {

    @Mock private IntervenantRepository intervenantRepository;
    @Mock private AvocatRepository avocatRepository;

    private IntervenantService service;

    @BeforeEach
    void setUp() {
        service = new IntervenantService(intervenantRepository, avocatRepository);
    }

    @Test
    void creerAvocat_nominal() {
        when(avocatRepository.findByCompteKeycloak("kc-1")).thenReturn(Optional.empty());
        when(avocatRepository.save(any(Avocat.class))).thenAnswer(inv -> {
            Avocat a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        IntervenantResponse result = service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", "me@cab.cm", "690000000", "kc-1", null));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.type()).isEqualTo(TypeIntervenant.AVOCAT);
        assertThat(result.compteKeycloak()).isEqualTo("kc-1");
        assertThat(result.notification()).isNull();
    }

    @Test
    void creerAvocat_sansCompteKeycloak_estRefuse() {
        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", null, null, "  ", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("compte Keycloak");
        verify(avocatRepository, never()).save(any());
    }

    @Test
    void creerAvocat_avecCompteDejaUtilise_estRefuse() {
        when(avocatRepository.findByCompteKeycloak("kc-1"))
                .thenReturn(Optional.of(Avocat.builder().id(9L).compteKeycloak("kc-1").build()));

        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Doublon", null, null, "kc-1", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà rattaché")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getChamp)
                .isEqualTo("compteKeycloak");
    }

    @Test
    void creerAvocat_avecNotification_estRefuse() {
        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", null, null, "kc-2", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("notification");
    }

    @Test
    void creerAutrePrestataire_nominal() {
        when(intervenantRepository.save(any(AutrePrestataire.class))).thenAnswer(inv -> {
            AutrePrestataire p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        IntervenantResponse result = service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AUTRE_PRESTATAIRE, "Notaire Kamga", "n@k.cm", null, null, true));

        assertThat(result.type()).isEqualTo(TypeIntervenant.AUTRE_PRESTATAIRE);
        assertThat(result.compteKeycloak()).isNull();
        assertThat(result.notification()).isTrue();
    }

    @Test
    void creerAutrePrestataire_avecCompteKeycloak_estRefuse() {
        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AUTRE_PRESTATAIRE, "Notaire", null, null, "kc-3", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("aucun compte");
    }

    @Test
    void lister_sansFiltre_retourneLesDeuxTypes() {
        when(intervenantRepository.findAll()).thenReturn(List.of(
                Avocat.builder().id(1L).nom("Me Fotso").compteKeycloak("kc-1").build(),
                AutrePrestataire.builder().id(2L).nom("Notaire").notification(false).build()));

        List<IntervenantResponse> result = service.lister(null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(IntervenantResponse::type)
                .containsExactly(TypeIntervenant.AVOCAT, TypeIntervenant.AUTRE_PRESTATAIRE);
    }

    @Test
    void lister_filtreParType() {
        List<Intervenant> tous = List.of(
                Avocat.builder().id(1L).nom("Me Fotso").compteKeycloak("kc-1").build(),
                AutrePrestataire.builder().id(2L).nom("Notaire").notification(false).build());
        when(intervenantRepository.findAll()).thenReturn(tous);

        assertThat(service.lister(TypeIntervenant.AVOCAT)).hasSize(1);
        assertThat(service.lister(TypeIntervenant.AUTRE_PRESTATAIRE)).hasSize(1);
    }
}
