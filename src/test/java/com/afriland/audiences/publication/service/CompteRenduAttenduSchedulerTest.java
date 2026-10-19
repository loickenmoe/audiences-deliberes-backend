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
import com.afriland.audiences.publication.repository.DemandeExplicationRepository;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
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
 * RG-INT-11: the lawyer is reminded as long as the report of a past hearing is missing.
 *
 * <p>Tests <b>when</b> a message is sent and above all when it is not: cadence, silence between
 * reminders, stop on upload, and the three cases where reminding would be wrong (cancelled hearing,
 * closed step, one reminder too many).
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
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private AlerteService alerteService;
    @Mock
    private DemandeExplicationRepository demandeExplicationRepository;

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
        // By default nobody owes an explanation: the reminder does its ordinary job.
        lenient().when(demandeExplicationRepository.avocatsEnAttenteDeReponse()).thenReturn(List.of());
    }

    /** A hearing {@code jours} days in the past, on a step in progress. */
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

    // What must be sent

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
     * The reference changes on each reminder: it prevents the engine from deduplicating the second
     * reminder with the first.
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

    /** Several lawyers on a case: each owes a report, each is reminded. */
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

    // What must NOT be sent

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

    /** A rejection is not an upload: the query only considers DEPOSE and VALIDE. */
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

    /** Beyond five unanswered reminders, silence: it is no longer an oversight. */
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
     * Cancelled hearings are excluded by the query, not in memory: checking it here ensures nobody can
     * widen the loading without the test failing.
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

    /** An unreadable configuration suspends the reminder, it does not bring down the whole night run. */
    @Test
    void uneConfigurationIllisible_suspendLaRelanceSansLeverDException() {
        when(configurationService.getOrThrow("DELAI_RAPPEL_CR_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().valeur("deux").build());

        scheduler.verifier();

        verify(audienceRepository, never()).findByDatePlanifieeBeforeAndStatutNot(any(), any());
        verify(notificationPrestataireService, never()).notifier(any(), any(), anyLong(), anyString());
    }

    /** Two hearings of the same case, one reported and one not: only the second triggers a reminder. */
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

    // N7: an explanation request suspends the reminder

    /**
     * The automated reminder stays silent while a manager is writing: reminding every two days while an
     * explanation request awaits its answer would weaken both.
     */
    @Test
    void relance_suspendue_quandUneExplicationEstAttendue() {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();
        when(demandeExplicationRepository.avocatsEnAttenteDeReponse()).thenReturn(List.of(99L));

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), any(), any(), anyString());
    }

    /**
     * It resumes as soon as the lawyer has answered: the report is still missing, and explaining is not
     * uploading.
     */
    @Test
    void relance_reprise_quandLExplicationEstDonnee() {
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();
        when(demandeExplicationRepository.avocatsEnAttenteDeReponse()).thenReturn(List.of());

        scheduler.verifier();

        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.COMPTE_RENDU_ATTENDU), eq(avocat), eq(DOSSIER_ID), anyString());
    }

    /** The exemption only applies to the lawyer who owes an explanation, not to colleagues. */
    @Test
    void relance_suspendue_pourLeSeulAvocatConcerne() {
        Avocat confrere = Avocat.builder().id(77L).nom("Cabinet NANA").build();
        when(dossierAvocatRepository.findByDossierId(DOSSIER_ID)).thenReturn(List.of(
                DossierAvocat.builder().avocat(avocat).build(),
                DossierAvocat.builder().avocat(confrere).build()));
        lesAudiencesPassees(audiencePassee(2, StatutCycleVie.EN_COURS));
        aucunCompteRendu();
        when(demandeExplicationRepository.avocatsEnAttenteDeReponse()).thenReturn(List.of(99L));

        scheduler.verifier();

        verify(notificationPrestataireService, never()).notifier(any(), eq(avocat), any(), anyString());
        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.COMPTE_RENDU_ATTENDU), eq(confrere), eq(DOSSIER_ID), anyString());
    }

    // N7: two consecutive hearings without a report are escalated to the DJ

    private static final long AUTRE_AUDIENCE_ID = 43L;

    private Utilisateur directeur;
    private Utilisateur adjointe;

    /** A hearing {@code jours} days in the past, with its own identifier. */
    private Audience audiencePassee(long identifiant, long jours) {
        Dossier dossier = Dossier.builder().id(DOSSIER_ID).reference("DOS-2026-0001").build();
        EtapeProcedure etape = EtapeProcedure.builder().id(1L).statutCycleVie(StatutCycleVie.EN_COURS).build();
        return Audience.builder()
                .id(identifiant)
                .dossier(dossier)
                .etape(etape)
                .statut(StatutAudience.TENUE)
                .datePlanifiee(LocalDate.now().minusDays(jours))
                .build();
    }

    /**
     * Full management: the Legal Director and the deputy. Both are notified, since one may be absent
     * when a lawyer drops out.
     */
    private void laDirectionJuridique() {
        directeur = Utilisateur.builder().id(500L).nom("Dir").prenom("Juridique").build();
        adjointe = Utilisateur.builder().id(501L).nom("Adjointe").prenom("Dir").build();
        lenient().when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ"))
                .thenReturn(List.of(directeur));
        lenient().when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA"))
                .thenReturn(List.of(adjointe));
    }

    private void compteRenduDepose(long audienceId, boolean depose) {
        lenient().when(compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                eq(audienceId), any())).thenReturn(depose);
    }

    @Test
    void manquement_deuxAudiencesDeSuiteSansCompteRendu_alerteLeDirecteur() {
        laDirectionJuridique();
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 10), audiencePassee(AUTRE_AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);
        compteRenduDepose(AUTRE_AUDIENCE_ID, false);

        scheduler.signalerManquementsRepetes();

        ArgumentCaptor<String> declencheur = ArgumentCaptor.forClass(String.class);
        verify(alerteService).declencher(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), eq(DOSSIER_ID),
                eq(directeur), declencheur.capture());
        // The deputy is notified too: one person's absence must not delay the escalation.
        verify(alerteService).declencher(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), eq(DOSSIER_ID),
                eq(adjointe), anyString());
        // The marker carries the **first** hearing of the series: it prevents duplicates.
        assertThat(declencheur.getValue()).startsWith("[avocat:99;serie:42] ")
                .contains("Cabinet TAMO").contains("2 audiences");
    }

    /**
     * A single missed hearing is not escalated: the ordinary reminder handles it (RG-INT-11). Escalation
     * happens on the second miss, so as not to trivialize the alert.
     */
    @Test
    void manquement_uneSeuleAudience_neRemontePas() {
        laDirectionJuridique();
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
    }

    /**
     * A series broken by an upload is not escalated: the lawyer missed the oldest but reported the most
     * recent, and is back in order.
     */
    @Test
    void manquement_serieRompueParUnDepot_neRemontePas() {
        laDirectionJuridique();
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 10), audiencePassee(AUTRE_AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);
        compteRenduDepose(AUTRE_AUDIENCE_ID, true);

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
    }

    /**
     * An already escalated series wakes nobody. "Two hearings without a report" is a **state**: without
     * this guard, the DJ would get the same alert every morning until upload.
     */
    @Test
    void manquement_serieDejaSignalee_neRemontePasDeuxFois() {
        laDirectionJuridique();
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 10), audiencePassee(AUTRE_AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);
        compteRenduDepose(AUTRE_AUDIENCE_ID, false);
        // Already escalated to both.
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), anyLong(),
                anyString())).thenReturn(true);

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
    }

    /**
     * The deputy alone is enough: the escalation is sent even without an active Legal Director. She is a
     * relay, not just an extra recipient.
     */
    @Test
    void manquement_sansDirecteurMaisAvecAdjointe_remonteQuandMeme() {
        adjointe = Utilisateur.builder().id(501L).nom("Adjointe").prenom("Dir").build();
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(adjointe));
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 10), audiencePassee(AUTRE_AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);
        compteRenduDepose(AUTRE_AUDIENCE_ID, false);

        scheduler.signalerManquementsRepetes();

        verify(alerteService).declencher(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), eq(DOSSIER_ID),
                eq(adjointe), anyString());
    }

    /** With nobody in management, the job stays silent instead of failing. */
    @Test
    void manquement_aucunDestinataireActif_neTombePas() {
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of());

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
        // It does not even read hearings: nobody to notify, nothing to compute.
        verify(audienceRepository, never()).findByDatePlanifieeBeforeAndStatutNot(any(), any());
    }
}
