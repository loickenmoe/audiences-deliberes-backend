package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.service.Route.MotifNonJoignable;
import com.afriland.audiences.notification.service.RoutageNotification.CoordonneesPrestataire;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPrestataireServiceTest {

    @Mock private RoutageNotification routage;
    @Mock private ReferencesDossier referencesDossier;
    @Mock private CompteAvocatService compteAvocatService;
    @Mock private NotificationEnvoiRepository repository;

    private NotificationPrestataireService service;

    private final Avocat avocat = Avocat.builder().id(9L).nom("Me Fotso").email("prestataire.test@example.cm")
            .telephone("600 00 00 00").compteKeycloak("kc-avocat").notification(true).build();
    private final AutrePrestataire notaire = AutrePrestataire.builder().id(11L).nom("Étude Mbarga")
            .email("notaire@etude.cm").telephone("600000000").notification(true).build();

    @BeforeEach
    void preparer() {
        service = new NotificationPrestataireService(routage, new RedacteurNotification(),
                new LiensApplication("http://localhost:3000"), referencesDossier, compteAvocatService, repository);
        lenient().when(repository.existsByEventId(anyString())).thenReturn(false);
    }

    @SuppressWarnings("unchecked")
    private List<NotificationEnvoi> enregistres() {
        ArgumentCaptor<List<NotificationEnvoi>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void router(Route... routes) {
        when(routage.pourPrestataire(any(), any(CoordonneesPrestataire.class))).thenReturn(List.of(routes));
    }

    @Test
    void un_avocat_avec_compte_recoit_le_lien_de_son_espace() {
        router(new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "prestataire.test@example.cm", null),
                new Route("SMS", CanalEngine.SMS, "+237600000000", null));
        when(referencesDossier.reference(5L)).thenReturn(Optional.of("DOS-2026-014"));
        when(compteAvocatService.utilisateurDe(avocat)).thenReturn(Optional.of(Utilisateur.builder().id(3L).build()));

        service.notifier(EvenementPrestataire.AFFECTATION_DOSSIER, avocat, 5L, "affectation-5");

        List<NotificationEnvoi> envois = enregistres();
        assertThat(envois).extracting(NotificationEnvoi::getEventId)
                .containsExactly("affectation-5-p9-EMAIL", "affectation-5-p9-SMS");
        NotificationEnvoi courriel = envois.get(0);
        assertThat(courriel.getDestinataireType()).isEqualTo(TypeDestinataire.AVOCAT);
        assertThat(courriel.getDestinataireRef()).isEqualTo(9L);
        assertThat(courriel.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(courriel.getSujet()).isEqualTo("[Audiences et délibérés] Nouveau dossier confié - DOS-2026-014");
        assertThat(courriel.getCorps()).contains("http://localhost:3000/portail");
        // Le SMS reste minimal : ni référence de dossier, ni lien.
        assertThat(envois.get(1).getCorps()).doesNotContain("DOS-2026-014", "http");
        assertThat(envois.get(1).getSujet()).isNull();
    }

    @Test
    void un_autre_prestataire_sans_compte_est_invite_a_contacter_la_direction_juridique() {
        router(new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "notaire@etude.cm", null));
        when(referencesDossier.reference(5L)).thenReturn(Optional.of("DOS-2026-014"));

        service.notifier(EvenementPrestataire.CONSTITUTION_VALIDEE, notaire, 5L, "constitution-3");

        NotificationEnvoi envoi = enregistres().get(0);
        assertThat(envoi.getDestinataireType()).isEqualTo(TypeDestinataire.AUTRE_PRESTATAIRE);
        assertThat(envoi.getCorps()).contains("contactez la Direction Juridique").doesNotContain("http");
    }

    @Test
    void le_consentement_de_la_fiche_est_transmis_au_routage() {
        Avocat refus = Avocat.builder().id(12L).nom("Me X").email("x@y.cm").notification(false).build();
        when(routage.pourPrestataire(any(), any(CoordonneesPrestataire.class))).thenReturn(List.of());

        service.notifier(EvenementPrestataire.FRAIS_REJETES, refus, 5L, "frais-1-rejet");

        ArgumentCaptor<CoordonneesPrestataire> captor = ArgumentCaptor.forClass(CoordonneesPrestataire.class);
        verify(routage).pourPrestataire(any(), captor.capture());
        assertThat(captor.getValue().consentement()).isFalse();
        assertThat(captor.getValue().courriel()).isEqualTo("x@y.cm");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void un_canal_sans_coordonnee_donne_une_ligne_non_joignable() {
        router(new Route("SMS", CanalEngine.SMS, null, MotifNonJoignable.COORDONNEE_INVALIDE));

        service.notifier(EvenementPrestataire.PAIEMENT_EFFECTUE, notaire, null, "frais-7-paiement");

        NotificationEnvoi envoi = enregistres().get(0);
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.NON_JOIGNABLE);
        assertThat(envoi.getDerniereErreur()).isEqualTo("COORDONNEE_INVALIDE");
    }

    /** Une relance planifiée ou une réaffectation ne doit pas heurter la contrainte d'unicité. */
    @Test
    void un_evenement_deja_en_file_est_ignore() {
        router(new Route("SMS", CanalEngine.SMS, "+237600000000", null));
        when(repository.existsByEventId("audience-8-J7-p11-SMS")).thenReturn(true);

        service.notifier(EvenementPrestataire.RAPPEL_AUDIENCE, notaire, 5L, "audience-8-J7");

        verify(repository, never()).saveAll(any());
    }

    @Test
    void sans_prestataire_rien_n_est_tente() {
        assertThatCode(() -> service.notifier(EvenementPrestataire.DELAI_RECOURS, null, 5L, "delibere-1-recours"))
                .doesNotThrowAnyException();
        verify(repository, never()).saveAll(any());
    }

    /** Une notification ne doit jamais faire échouer l'action métier qui la provoque. */
    @Test
    void un_echec_de_preparation_est_journalise_sans_remonter() {
        when(routage.pourPrestataire(any(), any(CoordonneesPrestataire.class)))
                .thenThrow(new IllegalStateException("réglage illisible"));

        assertThatCode(() -> service.notifier(EvenementPrestataire.CORRESPONDANCE_TRANSMISE, avocat, 5L, "correspondance-2"))
                .doesNotThrowAnyException();
        verify(repository, never()).saveAll(any());
    }
}
