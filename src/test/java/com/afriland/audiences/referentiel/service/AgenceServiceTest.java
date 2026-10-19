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
 * Agency reference data. What matters here is search pattern escaping, refusal of code changes, and
 * deactivation instead of deletion: each has a business reason and would break silently.
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

    /**
     * Typing part of an agency name progressively returns matching agencies. The search reaches the
     * repository wrapped in percent signs and lowercased, so it is partial and case-insensitive.
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
     * Without escaping, a user-typed percent would return all agencies and an underscore any character.
     * The escape character is the exclamation mark, declared in the query, so it is doubled too.
     */
    @Test
    void lister_echappeLesJokersEtLeCaractereDEchappement() {
        assertThat(AgenceService.motif("100%")).isEqualTo("%100!%%");
        assertThat(AgenceService.motif("MBALLA_II")).isEqualTo("%mballa!_ii%");
        assertThat(AgenceService.motif("Oh !")).isEqualTo("%oh !!%");
    }

    /** Empty search: no pattern at all, not an empty pattern that would find nothing. */
    @Test
    void lister_sansRechercheNePasseAucunMotif() {
        when(agenceRepository.rechercher(isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bonanjo)));

        assertThat(service.lister("   ", true, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
        assertThat(AgenceService.motif(null)).isNull();
    }

    /** The administration screen must see closed agencies: the flag is passed through as is. */
    @Test
    void lister_transmetActifSeulementTelQuel() {
        when(agenceRepository.rechercher(isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bonanjo)));

        service.lister(null, false, PageRequest.of(0, 20));

        verify(agenceRepository).rechercher(isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void creer_enregistreLAgenceEtLaRendActiveParDefaut() {
        when(agenceRepository.existsByCode("00117")).thenReturn(false);
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.creer(requete("  00117 ", " NOUVELLE  ", " Douala ", null));

        ArgumentCaptor<Agence> capture = ArgumentCaptor.forClass(Agence.class);
        verify(agenceRepository).save(capture.capture());
        // Input spaces are trimmed: "00117" and "00117  " must not coexist.
        assertThat(capture.getValue().getCode()).isEqualTo("00117");
        assertThat(capture.getValue().getNom()).isEqualTo("NOUVELLE");
        assertThat(capture.getValue().getVille()).isEqualTo("Douala");
        assertThat(capture.getValue().getActif()).isTrue();
        assertThat(reponse.actif()).isTrue();
    }

    /**
     * The code is unique: two agencies sharing it would skew agency reports, and a case would not know
     * which one it refers to. The conflict is a 409, not a 500 from the SQL constraint.
     */
    @Test
    void creer_refuseUnCodeDejaPorte() {
        when(agenceRepository.existsByCode("00002")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(requete("00002", "BONANJO BIS", "Douala", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("porte déjà le code 00002");

        verify(agenceRepository, never()).save(any());
    }

    /**
     * The code <b>cannot be changed</b>. Cases store it as plain text, without a foreign key: changing it
     * would silently detach them from their agency.
     */
    @Test
    void modifier_refuseLeChangementDeCode() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));

        assertThatThrownBy(() -> service.modifier(1L, requete("00999", "BONANJO", "Douala", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne se modifie pas");

        verify(agenceRepository, never()).save(any());
    }

    /** An agency can be renamed and moved: name, city, address and phone stay editable. */
    @Test
    void modifier_metAJourLeResteSansToucherAuCode() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        AgenceResponse reponse = service.modifier(1L,
                new AgenceRequest("00002", "BONANJO CENTRE", "Douala", null, null, null));

        assertThat(reponse.code()).isEqualTo("00002");
        assertThat(reponse.nom()).isEqualTo("BONANJO CENTRE");
        // Cleared address and phone mean "unknown", not "empty string". The city is mandatory: DTO
        // validation enforces it before reaching here.
        assertThat(reponse.adresse()).isNull();
        assertThat(reponse.telephone()).isNull();
        // `actif` missing from the request must not close the agency as a side effect.
        assertThat(reponse.actif()).isTrue();
    }

    @Test
    void modifier_signaleUneAgenceIntrouvable() {
        when(agenceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.modifier(404L, requete("00002", "BONANJO", "Douala", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Agence introuvable");
    }

    /**
     * Deactivate, never delete: a case opened under a closed agency stays valid, and its code must keep
     * referring to something years later.
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
     * Closing must be reversible. Otherwise a mistaken closure could only be fixed by recreating the
     * agency under another code, i.e. inventing a code. The code does not change: the same agency reopens.
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
     * Reopening an already open agency is not an error: the requested state is reached. Refusing would
     * force the caller to read before writing for nothing.
     */
    @Test
    void reactiver_estSansEffetSurUneAgenceDejaOuverte() {
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        assertThat(service.reactiver(1L).actif()).isTrue();
    }

    /** A reopened agency can again be selected when creating a case. */
    @Test
    void reactiver_rendLAgenceDeNouveauProposable() {
        bonanjo.setActif(false);
        when(agenceRepository.findById(1L)).thenReturn(Optional.of(bonanjo));
        when(agenceRepository.save(any(Agence.class))).thenAnswer(appel -> appel.getArgument(0));

        service.reactiver(1L);

        // `actif = true` is what gets written: it is the flag read by `existeEtEstActive`.
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

    /**
     * Case creation requires an <b>open</b> agency: offering a closed one on screen makes no sense, and
     * accepting it through the API would make both diverge.
     */
    @Test
    void existeEtEstActive_exigeUneAgenceOuverte() {
        when(agenceRepository.existsByCodeAndActifTrue("00002")).thenReturn(true);
        when(agenceRepository.existsByCodeAndActifTrue("00099")).thenReturn(false);

        assertThat(service.existeEtEstActive("  00002 ")).isTrue();
        assertThat(service.existeEtEstActive("00099")).isFalse();
    }

    /** Missing code: false, without querying the database. The calling service will refuse the case. */
    @Test
    void existeEtEstActive_refuseUnCodeAbsent() {
        assertThat(service.existeEtEstActive(null)).isFalse();

        verify(agenceRepository, never()).existsByCodeAndActifTrue(anyString());
    }
}
