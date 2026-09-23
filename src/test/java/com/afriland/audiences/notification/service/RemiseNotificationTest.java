package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Redirection;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.passerelle.JetonEngine;
import com.afriland.audiences.notification.passerelle.MessageEngine;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemiseNotificationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock private NotificationEnvoiRepository repository;
    @Mock private PasserelleNotificationEngine passerelle;

    /** Le moteur est déclaré actif dès que la redirection est en jeu : elle ne s'applique pas sinon. */
    private static NotificationEngineProperties proprietes(boolean redirection) {
        return new NotificationEngineProperties(redirection, "audience-deliberer", SECRET, 5, Duration.ofHours(2),
                new Redirection(redirection, "juriste.test@afrilandfirstbank.com", "prestataire.test@example.cm", "+237600000000"),
                new NotificationEngineProperties.Transport(NotificationEngineProperties.TypeTransport.JOURNAL, "t", "p", "u", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5));
    }

    private RemiseNotification service(NotificationEngineProperties proprietes) {
        return new RemiseNotification(repository, passerelle, new JetonEngine(proprietes),
                new RedirectionDestinataires(proprietes), new RappelsNotification(proprietes), proprietes);
    }

    private static NotificationEnvoi envoi(CanalEngine canal, TypeDestinataire type, String destinataire) {
        return NotificationEnvoi.aRemettre(
                new Origine("alerte-42", "RAPPEL_AUDIENCE", 5L, 42L, type, 7L), canal, destinataire, "Objet", "Texte");
    }

    /** {@code dateCreation} est posée par la base : on la simule pour éprouver la péremption. */
    private static NotificationEnvoi avecDateCreation(NotificationEnvoi envoi, LocalDateTime date) throws Exception {
        Field champ = NotificationEnvoi.class.getDeclaredField("dateCreation");
        champ.setAccessible(true);
        champ.set(envoi, date);
        return envoi;
    }

    private MessageEngine publie() {
        ArgumentCaptor<MessageEngine> captor = ArgumentCaptor.forClass(MessageEngine.class);
        verify(passerelle).publier(captor.capture());
        return captor.getValue();
    }

    @Test
    void une_notification_remise_porte_le_contrat_du_Guide() throws Exception {
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.UTILISATEUR,
                "juriste@afrilandfirstbank.com"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));
        when(passerelle.publier(any())).thenReturn("DAPR");

        service(proprietes(false)).remettre(1L);

        MessageEngine message = publie();
        assertThat(message.channels()).containsExactly("EMAIL");
        assertThat(message.recipient()).isEqualTo("juriste@afrilandfirstbank.com");
        assertThat(message.directSubject()).isEqualTo("Objet");
        assertThat(message.directBody()).isEqualTo("Texte");
        assertThat(message.eventId()).isEqualTo("alerte-42-EMAIL");
        assertThat(message.category()).isEqualTo("TRANSACTIONAL");
        assertThat(message.serviceKey()).isEqualTo("audience-deliberer");
        assertThat(message.priority()).isEqualTo("STANDARD");
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.PUBLIEE);
        assertThat(envoi.getStatutEngine()).isEqualTo("DAPR");
        assertThat(envoi.getDatePublication()).isNotNull();
        assertThat(envoi.getDestinataireOrigine()).isNull();
    }

    /** Le jeton identifie le service ; il est signé avec le secret partagé et vaut 5 minutes. */
    @Test
    void le_jeton_est_signe_HS256_avec_le_code_du_service() throws Exception {
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.TEAMS, TypeDestinataire.UTILISATEUR,
                "juriste@afrilandfirstbank.com"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));

        // Moteur actif : c'est le seul cas où un jeton est signé.
        service(proprietes(true)).remettre(1L);

        SignedJWT jeton = SignedJWT.parse(publie().token());
        assertThat(jeton.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jeton.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jeton.getJWTClaimsSet().getSubject()).isEqualTo("audience-deliberer");
        Instant expiration = jeton.getJWTClaimsSet().getExpirationTime().toInstant();
        assertThat(Duration.between(jeton.getJWTClaimsSet().getIssueTime().toInstant(), expiration))
                .isEqualTo(Duration.ofMinutes(5));
    }

    // ── Redirection hors production (D10) ──

    @Test
    void la_redirection_remplace_le_destinataire_et_conserve_le_reel() throws Exception {
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.SMS, TypeDestinataire.AVOCAT,
                "+237699887766"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));

        service(proprietes(true)).remettre(1L);

        assertThat(publie().recipient()).isEqualTo("+237600000000");
        assertThat(envoi.getDestinataire()).isEqualTo("+237600000000");
        assertThat(envoi.getDestinataireOrigine()).isEqualTo("+237699887766");
    }

    @Test
    void la_redirection_distingue_le_courriel_interne_du_courriel_personnel() throws Exception {
        NotificationEnvoi interne = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.UTILISATEUR,
                "juriste@afrilandfirstbank.com"), LocalDateTime.now());
        NotificationEnvoi prestataire = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.AUTRE_PRESTATAIRE,
                "notaire@etude.cm"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(interne));
        when(repository.findById(2L)).thenReturn(Optional.of(prestataire));
        RemiseNotification service = service(proprietes(true));

        service.remettre(1L);
        service.remettre(2L);

        ArgumentCaptor<MessageEngine> captor = ArgumentCaptor.forClass(MessageEngine.class);
        verify(passerelle, org.mockito.Mockito.times(2)).publier(captor.capture());
        assertThat(captor.getAllValues().get(0).recipient()).isEqualTo("juriste.test@afrilandfirstbank.com");
        assertThat(captor.getAllValues().get(1).recipient()).isEqualTo("prestataire.test@example.cm");
    }

    @Test
    void une_redirection_sans_cible_refuse_d_envoyer() throws Exception {
        NotificationEngineProperties incomplete = new NotificationEngineProperties(true, "audience-deliberer", SECRET,
                5, Duration.ofHours(2), new Redirection(true, "a@b.cm", "c@d.cm", null), new NotificationEngineProperties.Transport(NotificationEngineProperties.TypeTransport.JOURNAL, "t", "p", "u", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5));
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.SMS, TypeDestinataire.AVOCAT, "+237699887766"),
                LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));

        service(incomplete).remettre(1L);

        // L'erreur est traitée comme un échec de remise : rien ne part vers le vrai numéro.
        verify(passerelle, never()).publier(any());
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(envoi.getDerniereErreur()).contains("sans destinataire de test");
    }

    // ── Tentatives et péremption (D13) ──

    @Test
    void un_echec_replanifie_selon_les_delais_prevus_puis_abandonne() throws Exception {
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.UTILISATEUR,
                "juriste@afrilandfirstbank.com"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));
        when(passerelle.publier(any())).thenThrow(new IllegalStateException("courtier injoignable"));
        RemiseNotification service = service(proprietes(false));

        for (int tentative = 1; tentative <= 4; tentative++) {
            service.remettre(1L);
            assertThat(envoi.getStatut()).as("tentative " + tentative).isEqualTo(StatutEnvoi.EN_ATTENTE);
            assertThat(envoi.getTentatives()).isEqualTo(tentative);
            assertThat(envoi.getProchaineTentative()).isAfter(LocalDateTime.now());
            assertThat(envoi.getDerniereErreur()).isEqualTo("courtier injoignable");
        }

        service.remettre(1L);

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ECHEC);
        assertThat(envoi.getTentatives()).isEqualTo(5);
        assertThat(envoi.getProchaineTentative()).isNull();
    }

    @Test
    void les_delais_suivent_1min_5min_15min_1h_puis_plus_rien() {
        RemiseNotification service = service(proprietes(false));

        assertThat(service.prochaineTentative(0)).isBetween(LocalDateTime.now(), LocalDateTime.now().plusMinutes(2));
        assertThat(service.prochaineTentative(3)).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusHours(2));
        assertThat(service.prochaineTentative(4)).isNull();
    }

    /** Sans cette garde, la file accumulée avant l'activation partirait d'un seul bloc. */
    /** Moteur coupé : la passerelle journal consomme la file, sans exiger de destinataires de test. */
    @Test
    void moteur_coupe_la_file_se_vide_sans_redirection() throws Exception {
        NotificationEngineProperties coupe = new NotificationEngineProperties(false, "audience-deliberer", SECRET, 5,
                Duration.ofHours(2), new Redirection(true, null, null, null), new NotificationEngineProperties.Transport(NotificationEngineProperties.TypeTransport.JOURNAL, "t", "p", "u", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5));
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.UTILISATEUR,
                "juriste@afrilandfirstbank.com"), LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));
        when(passerelle.publier(any())).thenReturn("JOURNAL");

        service(coupe).remettre(1L);

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.PUBLIEE);
        assertThat(envoi.getStatutEngine()).isEqualTo("JOURNAL");
        assertThat(publie().recipient()).isEqualTo("juriste@afrilandfirstbank.com");
        // Aucun jeton signé tant que rien ne part : le secret n'est pas nécessaire pour vider la file.
        assertThat(publie().token()).isNull();
    }

    @Test
    void une_notification_trop_ancienne_est_abandonnee_sans_etre_envoyee() throws Exception {
        NotificationEnvoi envoi = avecDateCreation(envoi(CanalEngine.SMS, TypeDestinataire.AVOCAT, "+237600000000"),
                LocalDateTime.now().minusHours(3));
        when(repository.findById(1L)).thenReturn(Optional.of(envoi));

        service(proprietes(true)).remettre(1L);

        verify(passerelle, never()).publier(any());
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.ECHEC);
        assertThat(envoi.getDerniereErreur()).isEqualTo(RemiseNotification.MOTIF_PEREMPTION);
    }

    @Test
    void une_ligne_deja_traitee_ou_absente_est_ignoree() throws Exception {
        NotificationEnvoi publiee = avecDateCreation(envoi(CanalEngine.EMAIL, TypeDestinataire.UTILISATEUR, "a@b.cm"),
                LocalDateTime.now());
        publiee.marquerPubliee("DAPR", "a@b.cm");
        when(repository.findById(1L)).thenReturn(Optional.of(publiee));
        when(repository.findById(2L)).thenReturn(Optional.empty());
        RemiseNotification service = service(proprietes(false));

        service.remettre(1L);
        service.remettre(2L);

        verify(passerelle, never()).publier(any());
    }

    @Test
    void sans_secret_le_jeton_est_refuse_plutot_que_produit_invalide() {
        NotificationEngineProperties sansSecret = new NotificationEngineProperties(false, "audience-deliberer", "court",
                5, Duration.ofHours(2), new Redirection(false, null, null, null), new NotificationEngineProperties.Transport(NotificationEngineProperties.TypeTransport.JOURNAL, "t", "p", "u", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5));

        assertThatThrownBy(() -> new JetonEngine(sansSecret).signer())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trop court");
    }
}
