package com.afriland.audiences.publication.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CompteRenduAudienceRepository;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RG-INT-11 : l'avocat est relancé tant que le compte rendu d'une audience passée manque.
 *
 * <p>Ce qui est éprouvé ici n'est pas « un message part », mais <b>quand</b> il part et surtout
 * quand il ne part pas : la cadence, le silence entre deux relances, l'arrêt au dépôt, et les trois
 * cas où relancer serait une erreur — audience annulée, étape close, relance de trop.
 */
@ExtendWith(MockitoExtension.class)
class CompteRenduAttenduSchedulerTest {

    private static final long AUDIENCE_ID = 42L;
    private static final long DOSSIER_ID = 7L;

    @Mock
    private AudienceRepository audienceRepository;
    @Mock
    private CompteRenduAudienceRepository compteRenduAudienceRepository;
    @Mock
    private DossierAvocatRepository dossierAvocatRepository;
    @Mock
    private NotificationPrestataireService notificationPrestataireService;
    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private CompteRenduAttenduScheduler scheduler;

    private Avocat avocat;

    @BeforeEach
    void delaiDeDeuxJours() {
        lenient().when(configurationService.getOrThrow("DELAI_RAPPEL_CR_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().valeur("2").build());
        avocat = Avocat.builder().id(99L).nom("Cabinet TAMO").build();
        lenient().when(dossierAvocatRepository.findByDossierId(DOSSIER_ID))
                .thenReturn(List.of(DossierAvocat.builder().avocat(avocat).build()));
    }

    /** Une audience passée de {@code jours}, sur une étape en cours. */
    private Audience audiencePassee(long jours, StatutCycleVie statutEtape) {
        Dossier dossier = Dossier.builder().id(DOSSIER_ID).reference("DOS-2026-0001").build();
        EtapeProcedure etape = EtapeProcedure.builder().id(1L).statutCycleVie(statutEtape).build();
        return Audience.builder()
                .id(AUDIENCE_ID)
                .dossier(dossier)
                .etape(etape)
                .statut(StatutAudience.TENUE)
                .datePlanifiee(LocalDate.now().minusDays(jours))
                .build();
    }

    private void aucunCompteRendu() {
        when(compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                eq(AUDIENCE_ID), any())).thenReturn(false);
    }

    private void lesAudiencesPassees(Audience... audiences) {
        when(audienceRepository.findByDatePlanifieeBeforeAndStatutNot(
                any(LocalDate.class), eq(StatutAudience.ANNULEE))).thenReturn(List.of(audiences));
    }

    // ── Ce qui doit partir ────────────────────────────────────────────────────

    @Test
    void relance_auTermeDuDelai() {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();

        scheduler.verifier();

        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.COMPTE_RENDU_ATTENDU), eq(avocat), eq(DOSSIER_ID),
                reference.capture());
        assertThat(reference.getValue()).isEqualTo("cr-attendu-42-relance-1");
    }

    /**
     * La référence change à chaque relance : c'est elle qui empêche le moteur de dédoublonner le
     * deuxième rappel avec le premier, et donc de n'en envoyer jamais qu'un.
     */
    @ParameterizedTest
    @ValueSource(longs = {2, 4, 6, 8, 10})
    void relance_aChaqueMultipleDuDelai_avecUneReferenceDistincte(long jours) {
        lesAudiencesPassees(audiencePassee(jours, StatutCycleVie.EN_COURS));
        aucunCompteRendu();

        scheduler.verifier();

        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.COMPTE_RENDU_ATTENDU), eq(avocat), eq(DOSSIER_ID),
                eq("cr-attendu-42-relance-" + (jours / 2)));
    }

    /** Plusieurs avocats sur un dossier : chacun doit son compte rendu, chacun est relancé. */
    @Test
    void relance_chacunDesAvocatsAffectes() {
        Avocat second = Avocat.builder().id(100L).nom("Cabinet NANA").build();
        when(dossierAvocatRepository.findByDossierId(DOSSIER_ID)).thenReturn(List.of(
                DossierAvocat.builder().avocat(avocat).build(),
                DossierAvocat.builder().avocat(second).build()));
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();

        scheduler.verifier();

        verify(notificationPrestataireService).notifier(any(), eq(avocat), anyLong(), anyString());
        verify(notificationPrestataireService).notifier(any(), eq(second), anyLong(), anyString());
    }

    // ── Ce qui ne doit PAS partir ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(longs = {1, 3, 5, 7})
    void silence_entreDeuxRelances(long jours) {
        lesAudiencesPassees(audiencePassee(jours, StatutCycleVie.EN_COURS));

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    @Test
    void silence_quandLeCompteRenduEstDepose() {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        when(compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                eq(AUDIENCE_ID), any())).thenReturn(true);

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    /** Un rejet ne vaut pas dépôt : la requête ne consulte que DEPOSE et VALIDE. */
    @Test
    void unCompteRenduRejete_neComptePasCommeDepose() {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();

        scheduler.verifier();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<StatutPublication>> statuts =
                ArgumentCaptor.forClass(Collection.class);
        verify(compteRenduAudienceRepository).existsByAudienceIdAndStatutValidationIn(
                eq(AUDIENCE_ID), statuts.capture());
        assertThat(statuts.getValue())
                .containsExactlyInAnyOrder(StatutPublication.DEPOSE, StatutPublication.VALIDE)
                .doesNotContain(StatutPublication.REJETE);
    }

    /** Au-delà de cinq rappels sans réponse, le silence : ce n'est plus un oubli. */
    @Test
    void silence_passeLeNombreMaximalDeRelances() {
        lesAudiencesPassees(audiencePassee(12, StatutCycleVie.EN_COURS));

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOTURE", "ARCHIVE"})
    void silence_quandLEtapeEstFermee(String statut) {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.valueOf(statut)));

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    /**
     * Les audiences annulées ne sont pas écartées en mémoire mais par la requête : la vérifier ici
     * garantit que personne ne pourra élargir le chargement sans voir le test rougir.
     */
    @Test
    void lesAudiencesAnnulees_sontEcarteesParLaRequete() {
        lesAudiencesPassees();

        scheduler.verifier();

        verify(audienceRepository).findByDatePlanifieeBeforeAndStatutNot(
                any(LocalDate.class), eq(StatutAudience.ANNULEE));
    }

    @Test
    void silence_quandAucunAvocatNEstAffecte() {
        when(dossierAvocatRepository.findByDossierId(DOSSIER_ID)).thenReturn(List.of());
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    /** Une configuration illisible suspend la relance, elle ne fait pas tomber la nuit entière. */
    @Test
    void uneConfigurationIllisible_suspendLaRelanceSansLeverDException() {
        when(configurationService.getOrThrow("DELAI_RAPPEL_CR_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().valeur("deux").build());

        scheduler.verifier();

        verify(audienceRepository, never()).findByDatePlanifieeBeforeAndStatutNot(any(), any());
        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    /** Deux audiences du même dossier, l'une rendue et l'autre non : seule la seconde relance. */
    @Test
    void chaqueAudienceEstJugeeSeparement() {
        Audience rendue = audiencePassee(2, StatutCycleVie.EN_COURS);
        Audience muette = audiencePassee(2, StatutCycleVie.EN_COURS);
        muette.setId(43L);
        lesAudiencesPassees(rendue, muette);
        when(compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(eq(AUDIENCE_ID), any()))
                .thenReturn(true);
        when(compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(eq(43L), any()))
                .thenReturn(false);

        scheduler.verifier();

        verify(notificationPrestataireService, times(1))
                .notifier(any(), any(), anyLong(), eq("cr-attendu-43-relance-1"));
        verify(notificationPrestataireService, never())
                .notifier(any(), any(), anyLong(), eq("cr-attendu-42-relance-1"));
    }
}
