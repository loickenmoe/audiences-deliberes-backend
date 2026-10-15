package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Agence;
import com.afriland.audiences.referentiel.repository.AgenceRepository;
import com.afriland.audiences.referentiel.web.dto.AgenceRequest;
import com.afriland.audiences.referentiel.web.dto.AgenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le référentiel des agences (métier, 2026-10-03, observation 2).
 *
 * <p>Ce qui se joue ici n'est pas du mapping : c'est l'échappement du motif de recherche, le refus
 * du changement de code, et la désactivation qui remplace la suppression. Chacun de ces trois
 * points a une raison métier, et chacun casserait en silence.
 */
@ExtendWith(MockitoExtension.class)
class AgenceServiceTest {

    @Mock private AgenceRepository agenceRepository;

    private AgenceService service;

    private Agence bonanjo;

    @BeforeEach
    void setUp() {
        service = new AgenceService(agenceRepository);
        bonanjo = Agence.builder()
                .id(1L)
                .code("00002")
                .nom("BONANJO")
                .ville("Douala")
                .adresse("Quartier administratif Bonanjo")
                .telephone("233 42 68 00")
                .actif(true)
                .build();
    }

    private static AgenceRequest requete(String code, String nom, String ville, Boolean actif) {
        return new AgenceRequest(code, nom, ville, "Une adresse", "233 00 00 00", actif);
    }

    // ------------------------------------------------------------------ recherche

    /**
     * Le métier l'a décrite ainsi : « si on commence par entrer le nom d'une agence, l'app nous
     * renvoie progressivement les agences avec ces noms ». La recherche descend donc au dépôt
     * encadrée de pourcents et en minuscules, pour être partielle et insensible à la casse.
     */
    @Test
    void lister_encadreLeMotifEtLeMetEnMinuscules() {
        when(agenceRepository.rechercher(eq("%bona%"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bonanjo)));

        PageResponse<AgenceResponse> page = service.lister("  BONA  ", true, PageRequest.of(0, 20));

        assertThat(page.content()).singleElement()
                .satisfies(agence -> {
                    assertThat(agence.code()).isEqualTo("00002");
                    assertThat(agence.ville()).isEqualTo("Douala");
                });
    }

    /**
     * Sans échappement, un pourcent saisi par l'utilisateur ramènerait tout le référentiel et un
     * souligné n'importe quel caractère : la liste déroulante proposerait les 82 agences à qui
     * cherche « 100% ». Le caractère d'échappement est le point d'exclamation, déclaré dans la
     * requête - il se double donc lui aussi.
     */
    @Test
    void lister_echappeLesJokersEtLeCaractereDEchappement() {
        assertThat(AgenceService.motif("100%")).isEqualTo("%100!%%");
        assertThat(AgenceService.motif("MBALLA_II")).isEqualTo("%mballa!_ii%");
        assertThat(AgenceService.motif("Oh !")).isEqualTo("%oh !!%");
    }

    /** Recherche vide : pas de motif du tout, et non un motif vide qui ne trouverait rien. */
    @Test
    void lister_sansRechercheNePasseAucunMotif() {
        when(agenceRepository.rechercher(isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bonanjo)));

        assertThat(service.lister("   ", true, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
        assertThat(AgenceService.motif(null)).isNull();
    }

    /** L'écran d'administration doit voir les agences fermées : le drapeau descend tel quel. */
    @Test
    void lister_transmetActifSeulementTelQuel() {
        when(agenceRepository.rechercher(isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bonanjo)));

        service.lister(null, false, PageRequest.of(0, 20));

        verify(agenceRepository).rechercher(isNull(), eq(false), any(Pageable.class));
    }

    // ------------------------------------------------------------------ création

    @Test
    void creer_enregistreLAgenceEtLaRendActiveParDefaut() {
        when(agenceRepository.existsByCode("00117")).thenReturn(false);
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.creer(requete("  00117 ", " NOUVELLE  ", " Douala ", null));

        ArgumentCaptor<Agence> capture = ArgumentCaptor.forClass(Agence.class);
        verify(agenceRepository).save(capture.capture());
        // Les espaces de saisie sont rognées : « 00117 » et « 00117  » ne doivent pas coexister.
        assertThat(capture.getValue().getCode()).isEqualTo("00117");
        assertThat(capture.getValue().getNom()).isEqualTo("NOUVELLE");
        assertThat(capture.getValue().getVille()).isEqualTo("Douala");
        assertThat(capture.getValue().getActif()).isTrue();
        assertThat(reponse.actif()).isTrue();
    }

    /**
     * Le code est unique : deux agences qui le partagent rendraient les rapports par agence faux,
     * et le dossier ne saurait plus laquelle il désigne. Le conflit est un 409, pas un 500 venu de
     * la contrainte SQL.
     */
    @Test
    void creer_refuseUnCodeDejaPorte() {
        when(agenceRepository.existsByCode("00002")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(requete("00002", "BONANJO BIS", "Douala", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("porte déjà le code 00002");

        verify(agenceRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ modification

    /**
     * Le code <b>ne se modifie pas</b>.
     *
     * <p>Les dossiers le portent en clair, sans clé étrangère : le changer les détacherait
     * silencieusement de leur agence, et personne ne le verrait avant un rapport annuel.
     */
    @Test
    void modifier_refuseLeChangementDeCode() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));

        assertThatThrownBy(() -> service.modifier(1L, requete("00999", "BONANJO", "Douala", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne se modifie pas");

        verify(agenceRepository, never()).save(any());
    }

    /** Une agence se renomme et se déplace : nom, ville, adresse et téléphone restent modifiables. */
    @Test
    void modifier_metAJourLeResteSansToucherAuCode() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.modifier(1L,
                new AgenceRequest("00002", "BONANJO CENTRE", "Douala", null, null, null));

        assertThat(reponse.code()).isEqualTo("00002");
        assertThat(reponse.nom()).isEqualTo("BONANJO CENTRE");
        // Adresse et téléphone effacés valent « inconnus », non « chaîne vide ». La ville, elle, est
        // obligatoire : la validation du DTO l'exige avant d'arriver ici.
        assertThat(reponse.adresse()).isNull();
        assertThat(reponse.telephone()).isNull();
        // `actif` absent de la requête ne doit pas fermer l'agence par effet de bord.
        assertThat(reponse.actif()).isTrue();
    }

    @Test
    void modifier_signaleUneAgenceIntrouvable() {
        when(agenceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.modifier(404L, requete("00002", "BONANJO", "Douala", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Agence introuvable");
    }

    // ------------------------------------------------------------------ désactivation

    /**
     * On désactive, on ne supprime pas : un dossier ouvert sous une agence fermée reste un dossier
     * valide, et son code doit continuer de désigner quelque chose des années après.
     */
    @Test
    void desactiver_fermeLAgenceSansLaSupprimer() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.desactiver(1L);

        assertThat(reponse.actif()).isFalse();
        verify(agenceRepository, never()).delete(any());
        verify(agenceRepository, never()).deleteById(anyLong());
    }

    @Test
    void desactiver_signaleUneAgenceIntrouvable() {
        when(agenceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactiver(404L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Agence introuvable");
    }

    /**
     * La fermeture doit être réversible (métier, 2026-10-06).
     *
     * <p>Sans réouverture, une fermeture par erreur n'avait qu'une issue : recréer l'agence sous un
     * autre code, c'est-à-dire inventer un code - ce que le référentiel existe pour empêcher. Le
     * code, lui, ne bouge pas : c'est la même agence qui rouvre.
     */
    @Test
    void reactiver_rouvreLAgenceSansToucherAuCode() {
        bonanjo.setActif(false);
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.reactiver(1L);

        assertThat(reponse.actif()).isTrue();
        assertThat(reponse.code()).isEqualTo("00002");
        assertThat(reponse.nom()).isEqualTo("BONANJO");
    }

    /**
     * Réouvrir une agence déjà ouverte n'est pas une erreur : l'état demandé est atteint. Refuser
     * obligerait l'appelant à lire avant d'écrire pour rien.
     */
    @Test
    void reactiver_estSansEffetSurUneAgenceDejaOuverte() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        assertThat(service.reactiver(1L).actif()).isTrue();
    }

    /** Une agence rouverte redevient proposable à la création d'un dossier. */
    @Test
    void reactiver_rendLAgenceDeNouveauProposable() {
        bonanjo.setActif(false);
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        service.reactiver(1L);

        // C'est bien `actif = true` qui est écrit : c'est ce drapeau que lit `existeEtEstActive`.
        ArgumentCaptor<Agence> capture = ArgumentCaptor.forClass(Agence.class);
        verify(agenceRepository).save(capture.capture());
        assertThat(capture.getValue().getActif()).isTrue();
    }

    @Test
    void reactiver_signaleUneAgenceIntrouvable() {
        when(agenceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactiver(404L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Agence introuvable");
    }

    // ----------------------------------------------- contrôle à la création d'un dossier

    /**
     * À la création d'un dossier, on exige une agence <b>ouverte</b> : proposer une agence fermée
     * n'aurait pas de sens à l'écran, et l'accepter par l'API ferait diverger les deux.
     */
    @Test
    void existeEtEstActive_exigeUneAgenceOuverte() {
        when(agenceRepository.existsByCodeAndActifTrue("00002")).thenReturn(true);
        when(agenceRepository.existsByCodeAndActifTrue("00099")).thenReturn(false);

        assertThat(service.existeEtEstActive("  00002 ")).isTrue();
        assertThat(service.existeEtEstActive("00099")).isFalse();
    }

    /** Code absent : faux, sans interroger la base. Le service appelant refusera le dossier. */
    @Test
    void existeEtEstActive_refuseUnCodeAbsent() {
        assertThat(service.existeEtEstActive(null)).isFalse();

        verify(agenceRepository, never()).existsByCodeAndActifTrue(anyString());
    }
}
