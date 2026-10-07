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
        // Par défaut, personne ne doit d'explication : la relance fait son travail ordinaire.
        lenient().when(demandeExplicationRepository.avocatsEnAttenteDeReponse()).thenReturn(List.of());
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

    // ── N7 temps 2 : une demande d'explication suspend la relance ─────────────

    /**
     * La machine se tait pendant qu'un responsable ecrit.
     *
     * <p>Continuer a envoyer un rappel automatique tous les deux jours pendant qu'une demande
     * d'explication attend sa reponse affaiblirait les deux : le rappel, qu'on cesse de lire, et la
     * demande, noyee dedans.
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
     * Et elle reprend des que l'avocat a repondu.
     *
     * <p>Le compte rendu manque toujours : s'etre explique n'est pas l'avoir depose, et c'est de
     * nouveau l'affaire du rappel automatique.
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

    /** La dispense ne vaut que pour celui qui doit une explication, pas pour ses confreres. */
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

    // ── N7 : deux audiences consecutives sans compte rendu remontent au DJ ────

    private static final long AUTRE_AUDIENCE_ID = 43L;

    private Utilisateur directeur;
    private Utilisateur adjointe;

    /** Une audience passee de {@code jours}, avec son propre identifiant. */
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
     * La direction au complet : le Directeur Juridique et son adjointe.
     *
     * <p>Les deux sont avertis (metier, 2026-10-07) : l'un des deux peut etre absent le jour ou un
     * avocat decroche, et un signalement qui attend un retour de conge n'en est plus un.
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
        // L'adjointe est avertie aussi : l'absence de l'un ne doit pas retarder le signalement.
        verify(alerteService).declencher(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), eq(DOSSIER_ID),
                eq(adjointe), anyString());
        // Le marqueur porte la **premiere** audience de la serie : c'est lui qui evite le doublon.
        assertThat(declencheur.getValue()).startsWith("[avocat:99;serie:42] ")
                .contains("Cabinet TAMO").contains("2 audiences");
    }

    /**
     * Une seule audience manquee ne remonte pas.
     *
     * <p>C'est la relance ordinaire qui s'en charge (RG-INT-11). Le metier a dit « au deuxieme
     * manquement » : deranger le DJ au premier banaliserait l'alerte.
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
     * Une serie rompue par un depot ne remonte pas.
     *
     * <p>L'avocat a manque la plus ancienne et rendu compte de la plus recente : il s'est remis en
     * regle, et nommer un comportement corrige ferait du bruit.
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
     * Une serie deja signalee ne reveille personne.
     *
     * <p>« Deux audiences sans compte rendu » est un **etat** : sans cette garde, le DJ recevrait
     * la meme alerte chaque matin jusqu'au depot.
     */
    @Test
    void manquement_serieDejaSignalee_neRemontePasDeuxFois() {
        laDirectionJuridique();
        lesAudiencesPassees(audiencePassee(AUDIENCE_ID, 10), audiencePassee(AUTRE_AUDIENCE_ID, 5));
        compteRenduDepose(AUDIENCE_ID, false);
        compteRenduDepose(AUTRE_AUDIENCE_ID, false);
        // Deja signale a l'un comme a l'autre.
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.MANQUEMENT_COMPTE_RENDU), anyLong(),
                anyString())).thenReturn(true);

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
    }

    /**
     * L'adjointe seule suffit : le signalement part meme sans Directeur Juridique actif.
     *
     * <p>C'est le but de l'ouvrir aux deux. Si la liste du DJ decidait du depart, l'adjointe ne
     * serait qu'un destinataire de plus, et non un relais.
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

    /** Sans personne a la direction, le traitement se tait au lieu de tomber. */
    @Test
    void manquement_aucunDestinataireActif_neTombePas() {
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of());

        scheduler.signalerManquementsRepetes();

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
        // Et il ne lit meme pas les audiences : personne a prevenir, rien a calculer.
        verify(audienceRepository, never()).findByDatePlanifieeBeforeAndStatutNot(any(), any());
    }
}
