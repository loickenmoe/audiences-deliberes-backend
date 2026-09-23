package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import com.afriland.audiences.intervenant.web.dto.ModifierIntervenantRequest;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** M15 (Q-68) — création d'un intervenant : les invariants RG-INT-01/02 sont portés par le service. */
@ExtendWith(MockitoExtension.class)
class IntervenantServiceTest {

    @Mock private IntervenantRepository intervenantRepository;
    @Mock private AvocatRepository avocatRepository;
    @Mock private HistoriqueActionService historiqueActionService;

    private IntervenantService service;

    @BeforeEach
    void setUp() {
        service = new IntervenantService(intervenantRepository, avocatRepository, historiqueActionService);
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
        // Q-119 : l'avocat consent par défaut aux canaux externes.
        assertThat(result.notification()).isTrue();
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
    void creerAvocat_sansConsentement_estAccepte() {
        when(avocatRepository.findByCompteKeycloak("kc-2")).thenReturn(Optional.empty());
        when(avocatRepository.save(any(Avocat.class))).thenAnswer(inv -> inv.getArgument(0));

        // Q-119 : le consentement de l'avocat se refuse à la création comme il se retire ensuite.
        IntervenantResponse result = service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", null, null, "kc-2", false));

        assertThat(result.notification()).isFalse();
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
        when(intervenantRepository.rechercher(any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                Avocat.builder().id(1L).nom("Me Fotso").compteKeycloak("kc-1").build(),
                AutrePrestataire.builder().id(2L).nom("Notaire").notification(false).build())));

        List<IntervenantResponse> result = service.lister(null, null, PageRequest.of(0, 10)).content();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(IntervenantResponse::type)
                .containsExactly(TypeIntervenant.AVOCAT, TypeIntervenant.AUTRE_PRESTATAIRE);
    }

    /**
     * Q-100 : le filtre de type est désormais **poussé en SQL** par le discriminant de la hiérarchie,
     * là où le service chargeait tout l'annuaire pour le trier en mémoire. Le test vérifie donc la
     * traduction du type en critère de requête, et non plus un filtrage Java.
     */
    @Test
    void lister_traduitLeTypeEnCritereDeRequete() {
        when(intervenantRepository.rechercher(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.lister(TypeIntervenant.AVOCAT, null, PageRequest.of(0, 10));
        verify(intervenantRepository).rechercher(eq(Boolean.TRUE), isNull(), any());

        service.lister(TypeIntervenant.AUTRE_PRESTATAIRE, null, PageRequest.of(0, 10));
        verify(intervenantRepository).rechercher(eq(Boolean.FALSE), isNull(), any());
    }

    /** Q-105 — la recherche au fil de la frappe : un nom vide ne doit pas devenir un critère. */
    @Test
    void lister_ignoreUnNomVide() {
        when(intervenantRepository.rechercher(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.lister(null, "   ", PageRequest.of(0, 10));

        verify(intervenantRepository).rechercher(isNull(), isNull(), any());
    }

    // ------------------------------------------------------------ Modification (PUT /intervenants)

    // ── Étape 11 : coordonnées contrôlées et mises en forme à la saisie ──

    /** Le moteur n'accepte que trois écritures : la fiche porte donc un numéro directement utilisable. */
    @Test
    void creerAvocat_metEnFormeLeTelephone() {
        when(avocatRepository.findByCompteKeycloak("kc-tel")).thenReturn(Optional.empty());
        when(avocatRepository.save(any(Avocat.class))).thenAnswer(inv -> inv.getArgument(0));

        IntervenantResponse result = service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", " me@cab.cm ", "699 88 77 66", "kc-tel", null));

        assertThat(result.telephone()).isEqualTo("+237699887766");
        assertThat(result.email()).isEqualTo("me@cab.cm");
    }

    /**
     * Refus immédiat, sur le champ : sans lui, la saisie fautive ne se voyait qu'à l'envoi, sous la
     * forme d'une ligne « non joignable » en supervision — trop tard, et loin de qui peut corriger.
     */
    @Test
    void creerAvocat_auTelephoneHorsFormat_estRefuse() {
        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Fotso", null, "0699887766", "kc-tel2", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Numéro de téléphone invalide");
        verify(avocatRepository, never()).save(any());
    }

    @Test
    void creerAutrePrestataire_auCourrielSansDomaine_estRefuse() {
        assertThatThrownBy(() -> service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AUTRE_PRESTATAIRE, "Notaire Kamga", "notaire@etude", null, null, true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("courriel invalide");
        verify(intervenantRepository, never()).save(any());
    }

    @Test
    void modifierIntervenant_metEnFormeEtRefuseUnNumeroInvalide() {
        when(intervenantRepository.findById(7L)).thenReturn(Optional.of(avocat()));
        when(intervenantRepository.save(any(Intervenant.class))).thenAnswer(inv -> inv.getArgument(0));

        IntervenantResponse corrige = service.modifier(7L,
                new ModifierIntervenantRequest("Me Fotso", "me@cab.cm", "+237 699 88 77 66", true));
        assertThat(corrige.telephone()).isEqualTo("+237699887766");

        assertThatThrownBy(() -> service.modifier(7L,
                new ModifierIntervenantRequest("Me Fotso", "me@cab.cm", "12345", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Numéro de téléphone invalide");
    }

    /** Une fiche sans coordonnée reste permise : tout prestataire n'a pas de numéro. */
    @Test
    void lesCoordonneesRestentFacultatives() {
        when(avocatRepository.findByCompteKeycloak("kc-sans")).thenReturn(Optional.empty());
        when(avocatRepository.save(any(Avocat.class))).thenAnswer(inv -> inv.getArgument(0));

        IntervenantResponse result = service.creer(new CreerIntervenantRequest(
                TypeIntervenant.AVOCAT, "Me Sans Contact", "  ", "", "kc-sans", null));

        assertThat(result.email()).isNull();
        assertThat(result.telephone()).isNull();
    }

    private Avocat avocat() {
        return Avocat.builder().id(7L).nom("Me Fotso").email("me@cab.cm").compteKeycloak("kc-1").build();
    }

    private AutrePrestataire huissier() {
        return AutrePrestataire.builder().id(8L).nom("Huissier Nkoa").notification(false).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detailsHistorises() {
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(isNull(), eq(TypeActionHistorique.INTERVENANT_MODIFIE), details.capture());
        return details.getValue();
    }

    @Test
    void modifierAvocat_corrigeSesCoordonnees_etGardeSonCompte() {
        when(intervenantRepository.findById(7L)).thenReturn(Optional.of(avocat()));
        when(intervenantRepository.save(any(Intervenant.class))).thenAnswer(inv -> inv.getArgument(0));

        IntervenantResponse result = service.modifier(7L,
                new ModifierIntervenantRequest("Me Fotso", "fotso@cab.cm", "690000000", null));

        // Le compte Keycloak est figé : il n'est pas dans la requête, il ne bouge pas.
        assertThat(result.compteKeycloak()).isEqualTo("kc-1");
        assertThat(result.type()).isEqualTo(TypeIntervenant.AVOCAT);
        // Le nom, identique, n'encombre pas la trace ; le téléphone, absent jusque-là, y figure
        // avec une ancienne valeur nulle.
        assertThat(detailsHistorises()).hasSize(6)
                .containsEntry("prestataireId", 7L)
                .containsEntry("intervenant", "Me Fotso")
                .containsEntry("ancienEmail", "me@cab.cm")
                .containsEntry("nouvelEmail", "fotso@cab.cm")
                .containsEntry("ancienTelephone", null)
                // Étape 11 : le numéro est mis en forme avant d'être enregistré et historisé.
                .containsEntry("nouveauTelephone", "+237690000000");
    }

    @Test
    void modifierAvocat_retireSonConsentement() {
        when(intervenantRepository.findById(7L)).thenReturn(Optional.of(avocat()));
        when(intervenantRepository.save(any(Intervenant.class))).thenAnswer(inv -> inv.getArgument(0));

        // Q-119 : coché par défaut, il se retire ; le changement est historisé comme les autres.
        IntervenantResponse result = service.modifier(7L, new ModifierIntervenantRequest("Me Fotso", null, null, false));

        assertThat(result.notification()).isFalse();
        assertThat(detailsHistorises()).containsEntry("ancienneNotification", true)
                .containsEntry("nouvelleNotification", false);
    }

    @Test
    void modifierAutrePrestataire_basculeLaNotification() {
        when(intervenantRepository.findById(8L)).thenReturn(Optional.of(huissier()));
        when(intervenantRepository.save(any(Intervenant.class))).thenAnswer(inv -> inv.getArgument(0));

        IntervenantResponse result = service.modifier(8L,
                new ModifierIntervenantRequest("Huissier Nkoa", null, null, true));

        assertThat(result.notification()).isTrue();
        assertThat(detailsHistorises()).containsEntry("ancienneNotification", false)
                .containsEntry("nouvelleNotification", true);
    }

    @Test
    void modifier_sansAucunChangement_nEcritRien() {
        when(intervenantRepository.findById(7L)).thenReturn(Optional.of(avocat()));

        service.modifier(7L, new ModifierIntervenantRequest(" Me Fotso ", "me@cab.cm", "", null));

        verify(intervenantRepository, never()).save(any());
        verifyNoInteractions(historiqueActionService);
    }

    @Test
    void modifierIntervenantInexistant_renvoie404() {
        when(intervenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.modifier(99L, new ModifierIntervenantRequest("X", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Intervenant introuvable");
    }
}
