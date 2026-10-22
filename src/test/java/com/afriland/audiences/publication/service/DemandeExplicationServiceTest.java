package com.afriland.audiences.publication.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.domain.DemandeExplication;
import com.afriland.audiences.publication.repository.DemandeExplicationRepository;
import com.afriland.audiences.publication.web.dto.DemanderExplicationRequest;
import com.afriland.audiences.publication.web.dto.RepondreExplicationRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The explanation request and its answer (N7).
 *
 * <p>Tests the four refusals that make this exchange a record rather than a conversation: no two
 * open requests at once, no answer to someone else's request, no second answer, and a lawyer sees
 * only their own.
 */
@ExtendWith(MockitoExtension.class)
class DemandeExplicationServiceTest {

    private static final long AVOCAT_ID = 99L;
    private static final long DOSSIER_ID = 7L;

    @Mock private DemandeExplicationRepository demandeExplicationRepository;
    @Mock private IntervenantRepository intervenantRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private CompteAvocatService compteAvocatService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private NotificationPrestataireService notificationPrestataireService;

    @InjectMocks
    private DemandeExplicationService service;

    private Avocat avocat;
    private Utilisateur directeur;
    private Dossier dossier;

    @BeforeEach
    void decor() {
        avocat = Avocat.builder().id(AVOCAT_ID).nom("Cabinet TAMO").build();
        directeur = Utilisateur.builder().id(500L).nom("Dir").prenom("Juridique").build();
        dossier = Dossier.builder().id(DOSSIER_ID).reference("DOS-2026-0001").build();
        lenient().when(demandeExplicationRepository.save(any()))
                .thenAnswer(invocation -> {
                    DemandeExplication enregistree = invocation.getArgument(0);
                    if (enregistree.getId() == null) {
                        enregistree.setId(1L);
                    }
                    return enregistree;
                });
    }

    private void laDirectionEcrit() {
        when(currentUserService.getCurrentUser()).thenReturn(directeur);
        when(intervenantRepository.findById(AVOCAT_ID)).thenReturn(Optional.of(avocat));
        when(demandeExplicationRepository.existsByAvocatIdAndDateReponseIsNull(AVOCAT_ID))
                .thenReturn(false);
    }

    // The request

    @Test
    @DisplayName("la direction écrit : l'échange est enregistré, tracé, et part dans les deux canaux")
    void demande_enregistreeTraceeEtNotifiee() {
        laDirectionEcrit();
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier));
        Utilisateur compteAvocat = Utilisateur.builder().id(900L).build();
        when(compteAvocatService.utilisateurDe(avocat)).thenReturn(Optional.of(compteAvocat));

        var reponse = service.demander(
                new DemanderExplicationRequest(AVOCAT_ID, DOSSIER_ID, "Deux audiences sans compte rendu."));

        assertThat(reponse.enAttente()).isTrue();
        assertThat(reponse.avocatNom()).isEqualTo("Cabinet TAMO");
        assertThat(reponse.auteurNom()).isEqualTo("Juridique Dir");
        // The decision belongs to management, not the application: the history must say who made it.
        verify(historiqueActionService).enregistrerPour(eq(directeur), eq(DOSSIER_ID),
                eq(TypeActionHistorique.EXPLICATION_DEMANDEE), any());
        // In the application and outside it: the lawyer may no longer log in.
        verify(alerteService).declencher(eq(TypeAlerte.DEMANDE_EXPLICATION), eq(DOSSIER_ID),
                eq(compteAvocat), anyString());
        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.DEMANDE_EXPLICATION), eq(avocat), eq(DOSSIER_ID), anyString());
    }

    /**
     * One pending request at a time: a second would blur which one suspends the reminder and which one
     * to answer. Once answered, management can ask again.
     */
    @Test
    @DisplayName("une seconde demande est refusée tant que la première attend")
    void demande_refuseeSiUneAttendDeja() {
        // The refusal happens before knowing who writes: no author needed.
        when(intervenantRepository.findById(AVOCAT_ID)).thenReturn(Optional.of(avocat));
        when(demandeExplicationRepository.existsByAvocatIdAndDateReponseIsNull(AVOCAT_ID))
                .thenReturn(true);

        var requete = new DemanderExplicationRequest(AVOCAT_ID, null, "Encore une.");
        assertThatThrownBy(() -> service.demander(
                requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà en attente");

        verify(demandeExplicationRepository, never()).save(any());
    }

    /** Without a case, the request still exists: it concerns conduct, not a matter. */
    @Test
    @DisplayName("une demande sans dossier part, et ne s'historise sur aucun")
    void demande_sansDossier() {
        laDirectionEcrit();
        when(compteAvocatService.utilisateurDe(avocat)).thenReturn(Optional.empty());

        var reponse = service.demander(new DemanderExplicationRequest(AVOCAT_ID, null, "Expliquez-vous."));

        assertThat(reponse.dossierId()).isNull();
        // History is always recorded on a case: without a case, nothing to record.
        verify(historiqueActionService, never()).enregistrerPour(any(), anyLong(), any(), any());
        // But the external notification is sent: it reaches a logged-out lawyer.
        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.DEMANDE_EXPLICATION), eq(avocat), eq(null), anyString());
    }

    @Test
    @DisplayName("un identifiant qui n'est pas celui d'un avocat est refusé")
    void demande_avocatInconnu() {
        when(intervenantRepository.findById(AVOCAT_ID)).thenReturn(Optional.empty());

        var requete = new DemanderExplicationRequest(AVOCAT_ID, null, "Expliquez-vous.");
        assertThatThrownBy(() -> service.demander(
                requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucun avocat");
    }

    // The answer

    private DemandeExplication enAttente() {
        return DemandeExplication.builder()
                .id(1L).avocat(avocat).dossier(dossier).auteur(directeur)
                .contenu("Deux audiences sans compte rendu.")
                .dateDemande(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    @DisplayName("l'avocat répond : l'échange se clôt, et celui qui a demandé est prévenu")
    void reponse_enregistreeEtSignalee() {
        when(demandeExplicationRepository.findById(1L)).thenReturn(Optional.of(enAttente()));
        Utilisateur compteAvocat = Utilisateur.builder().id(900L).build();
        when(currentUserService.getCurrentUser()).thenReturn(compteAvocat);
        when(compteAvocatService.avocatDe(compteAvocat)).thenReturn(Optional.of(avocat));

        var reponse = service.repondre(1L, new RepondreExplicationRequest("Le greffe ne m'a pas transmis."));

        assertThat(reponse.enAttente()).isFalse();
        assertThat(reponse.reponse()).isEqualTo("Le greffe ne m'a pas transmis.");
        assertThat(reponse.dateReponse()).isNotNull();
        verify(historiqueActionService).enregistrerPour(eq(compteAvocat), eq(DOSSIER_ID),
                eq(TypeActionHistorique.EXPLICATION_REPONDUE), any());
        // Otherwise the requester would have to remember to check back.
        verify(alerteService).declencher(eq(TypeAlerte.DEMANDE_EXPLICATION), eq(DOSSIER_ID),
                eq(directeur), anyString());
    }

    @Test
    @DisplayName("un avocat ne répond pas à la demande adressée à un autre")
    void reponse_refuseeAUnTiers() {
        when(demandeExplicationRepository.findById(1L)).thenReturn(Optional.of(enAttente()));
        Utilisateur compteTiers = Utilisateur.builder().id(901L).build();
        when(currentUserService.getCurrentUser()).thenReturn(compteTiers);
        when(compteAvocatService.avocatDe(compteTiers))
                .thenReturn(Optional.of(Avocat.builder().id(77L).nom("Cabinet NANA").build()));

        var requete = new RepondreExplicationRequest("Ce n'est pas moi.");
        assertThatThrownBy(() -> service.repondre(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne vous est pas adressée");

        verify(demandeExplicationRepository, never()).save(any());
    }

    /**
     * An answer cannot be rewritten: it is a dated position that management may read months later.
     * Correcting goes through a new request.
     */
    @Test
    @DisplayName("une seconde réponse est refusée")
    void reponse_refuseeSiDejaDonnee() {
        DemandeExplication repondue = enAttente();
        repondue.setReponse("Déjà dit.");
        repondue.setDateReponse(LocalDateTime.now().minusHours(2));
        when(demandeExplicationRepository.findById(1L)).thenReturn(Optional.of(repondue));
        Utilisateur compteAvocat = Utilisateur.builder().id(900L).build();
        when(currentUserService.getCurrentUser()).thenReturn(compteAvocat);
        when(compteAvocatService.avocatDe(compteAvocat)).thenReturn(Optional.of(avocat));

        var requete = new RepondreExplicationRequest("Je me reprends.");
        assertThatThrownBy(() -> service.repondre(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà répondu");

        verify(demandeExplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("un compte non rattaché au répertoire ne répond pas")
    void reponse_refuseeSansAvocatRattache() {
        when(demandeExplicationRepository.findById(1L)).thenReturn(Optional.of(enAttente()));
        Utilisateur orphelin = Utilisateur.builder().id(902L).build();
        when(currentUserService.getCurrentUser()).thenReturn(orphelin);
        when(compteAvocatService.avocatDe(orphelin)).thenReturn(Optional.empty());

        var requete = new RepondreExplicationRequest("Bonjour.");
        assertThatThrownBy(() -> service.repondre(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucun avocat du référentiel");
    }
}
