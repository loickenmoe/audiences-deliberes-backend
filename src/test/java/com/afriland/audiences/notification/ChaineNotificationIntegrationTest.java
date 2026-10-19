package com.afriland.audiences.notification;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapperImpl;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.service.ReferencesDossierJpa;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.passerelle.MessageEngine;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.service.DispatcherNotification;
import com.afriland.audiences.notification.service.LiensApplication;
import com.afriland.audiences.notification.service.NotificationAlerteListener;
import com.afriland.audiences.notification.service.RappelsNotification;
import com.afriland.audiences.notification.service.RedacteurNotification;
import com.afriland.audiences.notification.service.RedirectionDestinataires;
import com.afriland.audiences.notification.service.ReglagesNotificationService;
import com.afriland.audiences.notification.service.RemiseNotification;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.notification.service.RoutageNotification;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AutrePrestataireRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 13: the whole chain on a real PostgreSQL. A business action raises an alert, routing and
 * drafting turn it into an external notification, the dispatcher hands it to the transport.
 * <p>
 * Each link has its own tests; this one checks they chain together. The transport is a test gateway
 * that keeps messages instead of sending them, so the test sees <b>exactly what would have been
 * sent</b>.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EnableConfigurationProperties(com.afriland.audiences.notification.config.NotificationEngineProperties.class)
@Import({AlerteService.class, AlerteMapperImpl.class, NotificationAlerteListener.class, RoutageNotification.class,
        ReglagesNotificationService.class, RedacteurNotification.class, LiensApplication.class,
        ReferencesDossierJpa.class, RemiseNotification.class, DispatcherNotification.class,
        RedirectionDestinataires.class, com.afriland.audiences.notification.passerelle.JetonEngine.class,
        RappelsNotification.class, NotificationPrestataireService.class, CompteAvocatService.class,
        ChaineNotificationIntegrationTest.PasserelleDEssai.class})
class ChaineNotificationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_chaine")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Engine "active" so redirection and token apply, but the test gateway sends nothing: no message
        // leaves the test.
        registry.add("notification.engine.actif", () -> "true");
        registry.add("notification.engine.code-service", () -> "audience-deliberer");
        registry.add("notification.engine.jwt-secret", () -> "0123456789abcdef0123456789abcdef");
        registry.add("notification.engine.redirection.actif", () -> "true");
        registry.add("notification.engine.redirection.courriel-interne", () -> "interne.test@afrilandfirstbank.com");
        registry.add("notification.engine.redirection.courriel-personnel", () -> "perso.test@example.cm");
        registry.add("notification.engine.redirection.telephone", () -> "600000000");
    }

    /** Keeps what would have been sent instead of sending it. */
    @TestConfiguration
    static class PasserelleDEssai {
        static final List<MessageEngine> MESSAGES = new CopyOnWriteArrayList<>();

        @Bean
        PasserelleNotificationEngine passerelle() {
            return message -> {
                MESSAGES.add(message);
                return "ESSAI";
            };
        }
    }

    @MockBean private SimpMessagingTemplate messagingTemplate;
    @MockBean private SimpUserRegistry simpUserRegistry;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private AlerteService alerteService;
    @Autowired private DispatcherNotification dispatcher;
    @Autowired private RemiseNotification remise;
    @Autowired private NotificationEnvoiRepository notificationEnvoiRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ProfilRepository profilRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AutrePrestataireRepository autrePrestataireRepository;
    @Autowired private NotificationPrestataireService notificationPrestataireService;
    @Autowired private EntityManager entityManager;

    private Utilisateur juriste(String keycloakId, String email) {
        return utilisateurRepository.save(Utilisateur.builder()
                .keycloakId(keycloakId).nom("Test").prenom("Juriste").email(email)
                .profil(profilRepository.findByCode("JURISTE").orElseThrow()).build());
    }

    private Long dossier(String reference) {
        Long clientId = jdbc.queryForObject("INSERT INTO client (reference, nom) VALUES (?, 'Client') RETURNING id",
                Long.class, "CLI-" + reference);
        return jdbc.queryForObject("INSERT INTO dossier (reference, nature, categorie, juridiction_saisie, client_id,"
                + " incident_compte_reference) VALUES (?, 'SOCIAL', 'EXPLOITATION_LITIGES', 'TPI', ?, 'INC') RETURNING id",
                Long.class, reference, clientId);
    }

    @Test
    void d_une_alerte_metier_jusqu_au_message_pret_a_partir() {
        PasserelleDEssai.MESSAGES.clear();
        Utilisateur juriste = juriste("kc-chaine-1", "juriste.reel@afrilandfirstbank.com");
        Long dossierId = dossier("DOS-CHAINE-1");

        // 1. The business action raises the alert, with its internal text (markers and codes included).
        var alerte = alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, dossierId, juriste,
                "[audienceId:5;J-7] Audience prévue (RG-AUD-03) — motif interne");

        // 2. The external notification is queued in the same transaction.
        List<NotificationEnvoi> file = notificationEnvoiRepository.findAll();
        assertThat(file).hasSize(1);
        NotificationEnvoi envoi = file.get(0);
        assertThat(envoi.getEventId()).isEqualTo("alerte-" + alerte.getId() + "-EMAIL");
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);

        // 3. The dispatcher hands it to the transport.
        remise.remettre(envoi.getId());

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.PUBLIEE);
        assertThat(envoi.getStatutEngine()).isEqualTo("ESSAI");

        // 4. What would have been sent: the engine contract, redirection applied, no internal data.
        assertThat(PasserelleDEssai.MESSAGES).hasSize(1);
        MessageEngine message = PasserelleDEssai.MESSAGES.get(0);
        assertThat(message.channels()).containsExactly("EMAIL");
        assertThat(message.recipient()).isEqualTo("interne.test@afrilandfirstbank.com");
        assertThat(message.serviceKey()).isEqualTo("audience-deliberer");
        assertThat(message.priority()).isEqualTo("STANDARD");
        assertThat(message.token()).isNotBlank();
        assertThat(message.directSubject()).isEqualTo("[Audiences et délibérés] Rappel d'audience - DOS-CHAINE-1");
        assertThat(message.directBody())
                .contains("Une audience approche", "DOS-CHAINE-1")
                .doesNotContain("audienceId", "RG-AUD", "motif interne");
        // The real recipient is kept: we know whom the message would have reached in production.
        assertThat(envoi.getDestinataireOrigine()).isEqualTo("juriste.reel@afrilandfirstbank.com");
    }

    /** A channel disabled by the DJ stops the whole chain, as early as queueing. */
    @Test
    void un_canal_coupe_par_le_DJ_ne_produit_aucun_message() {
        PasserelleDEssai.MESSAGES.clear();
        jdbc.update("UPDATE configuration_systeme SET valeur = 'APPLICATIF' WHERE cle = 'CANAUX_NOTIFICATION_DEFAUT'");
        Utilisateur juriste = juriste("kc-chaine-2", "juriste2@afrilandfirstbank.com");

        alerteService.declencher(TypeAlerte.DOCUMENT_CHARGE, dossier("DOS-CHAINE-2"), juriste, "Document");
        dispatcher.remettreLesNotificationsEnAttente();

        assertThat(notificationEnvoiRepository.findAll()).isEmpty();
        assertThat(PasserelleDEssai.MESSAGES).isEmpty();
    }

    /** A type removed from the grid is not sent, while others continue. */
    @Test
    void la_grille_du_DJ_filtre_evenement_par_evenement() {
        PasserelleDEssai.MESSAGES.clear();
        jdbc.update("UPDATE configuration_systeme SET valeur = 'DELAI_RECOURS' WHERE cle = 'GRILLE_NOTIFICATION_OUTLOOK'");
        Utilisateur juriste = juriste("kc-chaine-3", "juriste3@afrilandfirstbank.com");
        Long dossierId = dossier("DOS-CHAINE-3");

        alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, dossierId, juriste, "Rappel");
        assertThat(notificationEnvoiRepository.findAll()).isEmpty();

        alerteService.declencher(TypeAlerte.DELAI_RECOURS, dossierId, juriste, "Délai");
        assertThat(notificationEnvoiRepository.findAll()).hasSize(1);
    }

    /**
     * A missing contact detail stops only its channel: the line stays visible in supervision and is
     * never sent, while the other channel proceeds.
     * <p>
     * Only applies to a provider: an internal user's address is mandatory and validated by the entity.
     */
    @Test
    void une_coordonnee_manquante_n_arrete_que_son_canal() {
        PasserelleDEssai.MESSAGES.clear();
        AutrePrestataire notaire = autrePrestataireRepository.save(AutrePrestataire.builder()
                .nom("Notaire d'essai").email("prestataire.test@example.cm").telephone(null)
                .notification(true).build());
        Long dossierId = dossier("DOS-CHAINE-4");

        notificationPrestataireService.notifier(EvenementPrestataire.CONSTITUTION_VALIDEE, notaire, dossierId,
                "constitution-404");
        dispatcher.remettreLesNotificationsEnAttente();

        // The two default provider channels: personal email and SMS.
        NotificationEnvoi sms = notificationEnvoiRepository.findByEventId("constitution-404-p"
                + notaire.getId() + "-SMS").orElseThrow();
        assertThat(sms.getStatut()).isEqualTo(StatutEnvoi.NON_JOIGNABLE);
        assertThat(sms.getDerniereErreur()).isEqualTo("COORDONNEE_ABSENTE");

        NotificationEnvoi courriel = notificationEnvoiRepository.findByEventId("constitution-404-p"
                + notaire.getId() + "-EMAIL").orElseThrow();
        assertThat(courriel.getStatut()).isEqualTo(StatutEnvoi.PUBLIEE);
        assertThat(PasserelleDEssai.MESSAGES).hasSize(1);
        assertThat(PasserelleDEssai.MESSAGES.get(0).channels()).containsExactly("EMAIL");
    }

    /** The in-app channel stays intact: it alone is mandatory (NF-ALR-02). */
    @Test
    void l_alerte_applicative_survit_a_une_panne_des_notifications_externes() {
        PasserelleDEssai.MESSAGES.clear();
        jdbc.update("UPDATE configuration_systeme SET valeur = 'PIGEON' WHERE cle = 'GRILLE_NOTIFICATION_OUTLOOK'");
        Utilisateur juriste = juriste("kc-chaine-5", "juriste5@afrilandfirstbank.com");

        var alerte = alerteService.declencher(TypeAlerte.DOCUMENT_CHARGE, dossier("DOS-CHAINE-5"), juriste, "Document");

        assertThat(alerte.getId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerte WHERE id = ?", Long.class, alerte.getId()))
                .isEqualTo(1);
    }

    @Test
    void une_notification_perimee_n_est_jamais_envoyee() {
        PasserelleDEssai.MESSAGES.clear();
        Utilisateur juriste = juriste("kc-chaine-6", "juriste6@afrilandfirstbank.com");
        alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, dossier("DOS-CHAINE-6"), juriste, "Rappel");
        NotificationEnvoi envoi = notificationEnvoiRepository.findAll().get(0);
        // Three hours: beyond the maximum age (2 h by default).
        notificationEnvoiRepository.flush();
        jdbc.update("UPDATE notification_envoi SET date_creation = date_creation - interval '3 hours' WHERE id = ?",
                envoi.getId());
        // The update bypasses Hibernate: otherwise delivery would reread the cached row.
        entityManager.clear();

        remise.remettre(envoi.getId());

        assertThat(PasserelleDEssai.MESSAGES).isEmpty();
        notificationEnvoiRepository.flush();
        assertThat(jdbc.queryForObject("SELECT statut FROM notification_envoi WHERE id = ?", String.class, envoi.getId()))
                .isEqualTo("ECHEC");
    }
}
