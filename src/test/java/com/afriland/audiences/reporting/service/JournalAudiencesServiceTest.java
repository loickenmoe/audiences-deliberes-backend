package com.afriland.audiences.reporting.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CompteRenduAudienceRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hearing log. Both reports are shown without one hiding the other, missed reports are derived
 * from the status without new data, and the current day is never counted as late.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalAudiencesServiceTest {

    @Mock private AudienceRepository audienceRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private CompteRenduAudienceRepository compteRenduAudienceRepository;
    @Mock private MinioStorageService minioStorageService;

    private static final Pageable PAGE = PageRequest.of(0, 10);

    private JournalAudiencesService service;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        service = new JournalAudiencesService(audienceRepository, dossierJuristeRepository,
                compteRenduAudienceRepository, minioStorageService);
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").juridictionSaisie("TGI Douala").build();
        Utilisateur juriste = Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build();
        when(dossierJuristeRepository.findByDossierId(1L))
                .thenReturn(List.of(DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build()));
    }

    private Audience audience(Long id, LocalDate date, StatutAudience statut, String compteRendu) {
        return Audience.builder()
                .id(id).dossier(dossier).datePlanifiee(date).statut(statut).compteRendu(compteRendu)
                .etape(EtapeProcedure.builder().id(5L).dossier(dossier).type(TypeEtape.INSTANCE).build())
                .build();
    }

    private CompteRenduAudience compteRenduAvocat(Audience audience, String contenu) {
        CompteRenduAudience compteRendu = new CompteRenduAudience();
        compteRendu.setAudience(audience);
        compteRendu.setContenu(contenu);
        compteRendu.setAvocat(Avocat.builder().id(77L).nom("Me Fotso").build());
        compteRendu.setStatutValidation(StatutPublication.VALIDE);
        return compteRendu;
    }

    @Test
    @DisplayName("le journal rapporte les deux comptes rendus côte à côte")
    void journal_porteLesDeuxComptesRendus() {
        Audience audience = audience(3L, LocalDate.of(2026, 3, 12), StatutAudience.TENUE, "Récit du juriste");
        when(audienceRepository.findByDatePlanifieeAndStatutNot(LocalDate.of(2026, 3, 12),
                StatutAudience.ANNULEE, PAGE)).thenReturn(new PageImpl<>(List.of(audience)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(List.of(3L),
                StatutPublication.VALIDE)).thenReturn(List.of(compteRenduAvocat(audience, "Récit de l'avocat")));

        PageResponse<AudienceDuJourResponse> journal = service.journal(LocalDate.of(2026, 3, 12), PAGE);

        assertThat(journal.content()).hasSize(1);
        var ligne = journal.content().get(0);
        assertThat(ligne.compteRenduJuriste()).isEqualTo("Récit du juriste");
        assertThat(ligne.compteRenduAvocat()).isEqualTo("Récit de l'avocat");
        assertThat(ligne.avocatDuCompteRendu()).isEqualTo(77L);
        assertThat(ligne.referenceDossier()).isEqualTo("DOS-2026-001");
        assertThat(ligne.juristesAffectes()).containsExactly(10L);
        assertThat(ligne.sansAucunCompteRendu()).isFalse();
    }

    /**
     * The document the lawyer attaches to the report (court record, hearing note) can be opened from the
     * log; otherwise the screen would show the narrative while hiding its supporting document.
     */
    @Test
    @DisplayName("la pièce jointe au compte rendu de l'avocat est consultable")
    void journal_donneAccesALaPieceJointeDeLAvocat() {
        Audience audience = audience(9L, LocalDate.of(2026, 3, 12), StatutAudience.TENUE, "Récit du juriste");
        CompteRenduAudience avocat = compteRenduAvocat(audience, "Récit de l'avocat");
        avocat.setCheminStockage("publications/12/plumitif.pdf");
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(audience)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(any(), any()))
                .thenReturn(List.of(avocat));
        when(minioStorageService.urlTelechargement("publications/12/plumitif.pdf"))
                .thenReturn("https://stockage/plumitif.pdf?signature=abc");

        var ligne = service.journal(LocalDate.of(2026, 3, 12), PAGE).content().get(0);

        assertThat(ligne.urlPieceJointeAvocat()).isEqualTo("https://stockage/plumitif.pdf?signature=abc");
    }

    /** A report without attachment does not invent one: the block has nothing to offer. */
    @Test
    void journal_sansPieceJointeNInterrogePasLeStockage() {
        Audience audience = audience(10L, LocalDate.of(2026, 3, 12), StatutAudience.TENUE, "Récit");
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(audience)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(any(), any()))
                .thenReturn(List.of(compteRenduAvocat(audience, "Récit de l'avocat")));

        var ligne = service.journal(LocalDate.of(2026, 3, 12), PAGE).content().get(0);

        assertThat(ligne.urlPieceJointeAvocat()).isNull();
        verify(minioStorageService, never()).urlTelechargement(any());
    }

    /** The case motivating the screen: the hearing took place, nobody reported on it. */
    @Test
    @DisplayName("une audience sans aucun compte rendu se signale comme telle")
    void journal_signaleUneAudienceMuette() {
        Audience audience = audience(4L, LocalDate.of(2026, 3, 12), StatutAudience.PLANIFIEE, null);
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(audience)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(any(), any()))
                .thenReturn(List.of());

        var ligne = service.journal(LocalDate.of(2026, 3, 12), PAGE).content().get(0);

        assertThat(ligne.compteRenduJuriste()).isNull();
        assertThat(ligne.compteRenduAvocat()).isNull();
        assertThat(ligne.sansAucunCompteRendu()).isTrue();
    }

    /**
     * An unvalidated upload is readable by no legal officer: counting it as reported would suggest
     * available information and hide a real omission.
     */
    @Test
    void journal_ignoreUnCompteRenduAvocatNonValide() {
        Audience audience = audience(5L, LocalDate.of(2026, 3, 12), StatutAudience.PLANIFIEE, null);
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(audience)));

        service.journal(LocalDate.of(2026, 3, 12), PAGE);

        verify(compteRenduAudienceRepository)
                .findByAudienceIdInAndStatutValidation(List.of(5L), StatutPublication.VALIDE);
    }

    @Test
    void journal_sansAudienceNInterrogePasLesPublications() {
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.journal(LocalDate.of(2026, 3, 12), PAGE).content()).isEmpty();
        verify(compteRenduAudienceRepository, never()).findByAudienceIdInAndStatutValidation(any(), any());
    }

    /**
     * Omissions are read from the status: a past hearing still {@code PLANIFIEE} has no report, since
     * entering the report moves it to {@code TENUE}.
     */
    @Test
    @DisplayName("les comptes rendus attendus sont les audiences passées restées planifiées")
    void attendus_listeLesAudiencesPasseesNonTenues() {
        LocalDate ilYaDixJours = LocalDate.now().minusDays(10);
        Audience audience = audience(6L, ilYaDixJours, StatutAudience.PLANIFIEE, null);
        when(audienceRepository.findByStatutAndDatePlanifieeBetween(
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(audience)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(any(), any()))
                .thenReturn(List.of(compteRenduAvocat(audience, "L'avocat, lui, a rapporté")));

        PageResponse<CompteRenduAttenduResponse> attendus =
                service.comptesRendusAttendus(LocalDate.now().minusDays(30), LocalDate.now(), PAGE);

        assertThat(attendus.content()).hasSize(1);
        assertThat(attendus.content().get(0).joursDeRetard()).isEqualTo(10);
        // The useful distinction: the bank has not written what the lawyer already reported.
        assertThat(attendus.content().get(0).compteRenduAvocatDepose()).isTrue();
        assertThat(attendus.content().get(0).juristesAffectes()).containsExactly(10L);

        verify(audienceRepository).findByStatutAndDatePlanifieeBetween(
                StatutAudience.PLANIFIEE, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), PAGE);
    }

    /** A hearing today is not late: the day is not over. */
    @Test
    @DisplayName("la journée en cours n'est jamais comptée comme un retard")
    void attendus_excluentLaJourneeEnCours() {
        // Mockito returns `null` for an unstubbed Page, where it returned an empty list.
        when(audienceRepository.findByStatutAndDatePlanifieeBetween(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.comptesRendusAttendus(LocalDate.now().minusDays(3), LocalDate.now().plusDays(5), PAGE);

        verify(audienceRepository).findByStatutAndDatePlanifieeBetween(
                StatutAudience.PLANIFIEE, LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), PAGE);
    }

    /** A fully future window blames nothing: it returns nothing and does not fail. */
    @Test
    void attendus_uneFenetreFutureNeRenvoieRien() {
        PageResponse<CompteRenduAttenduResponse> attendus =
                service.comptesRendusAttendus(LocalDate.now().plusDays(1), LocalDate.now().plusDays(10), PAGE);

        assertThat(attendus.content()).isEmpty();
        verify(audienceRepository, never())
                .findByStatutAndDatePlanifieeBetween(any(), any(), any(), any());
    }

    @Test
    void attendus_refusentUneFenetreInversee() {
        assertThatThrownBy(() -> service.comptesRendusAttendus(LocalDate.now(), LocalDate.now().minusDays(5), PAGE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("précéder");
    }

    @Test
    void journal_neChargeLesJuristesQueParDossier() {
        Audience premiere = audience(7L, LocalDate.of(2026, 3, 12), StatutAudience.TENUE, "A");
        Audience seconde = audience(8L, LocalDate.of(2026, 3, 12), StatutAudience.TENUE, "B");
        when(audienceRepository.findByDatePlanifieeAndStatutNot(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(premiere, seconde)));
        when(compteRenduAudienceRepository.findByAudienceIdInAndStatutValidation(any(), any()))
                .thenReturn(List.of());

        assertThat(service.journal(LocalDate.of(2026, 3, 12), PAGE).content()).hasSize(2);
        // A single publications query for the whole day, never one per row.
        verify(compteRenduAudienceRepository).findByAudienceIdInAndStatutValidation(List.of(7L, 8L),
                StatutPublication.VALIDE);
        verify(dossierJuristeRepository, org.mockito.Mockito.times(2)).findByDossierId(anyLong());
    }
}
