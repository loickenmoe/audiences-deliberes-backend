package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapperImpl;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.service.ReferencesDossierJpa;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Étape 6 sur PostgreSQL réel : une alerte déclenchée écrit ses notifications externes dans la même
 * transaction (D12), avec les réglages initiaux de V28 (Outlook seul pour les internes).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AlerteService.class, AlerteMapperImpl.class, NotificationAlerteListener.class, RoutageNotification.class,
        ReglagesNotificationService.class, RedacteurNotification.class, LiensApplication.class, ReferencesDossierJpa.class})
class NotificationAlerteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_notification_alerte")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean private SimpMessagingTemplate messagingTemplate;
    @MockBean private SimpUserRegistry simpUserRegistry;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private AlerteService alerteService;
    @Autowired private NotificationEnvoiRepository notificationEnvoiRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ProfilRepository profilRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private Utilisateur utilisateur(String keycloakId, String profil, String email) {
        return utilisateurRepository.save(Utilisateur.builder()
                .keycloakId(keycloakId).nom("Test").prenom(profil).email(email)
                .profil(profilRepository.findByCode(profil).orElseThrow()).build());
    }

    private Long dossier(String reference) {
        Long clientId = jdbc.queryForObject("INSERT INTO client (reference, nom) VALUES (?, 'Client test') RETURNING id",
                Long.class, "CLI-" + reference);
        return jdbc.queryForObject("INSERT INTO dossier (reference, nature, categorie, juridiction_saisie, client_id, incident_compte_reference) "
                + "VALUES (?, 'SOCIAL', 'EXPLOITATION_LITIGES', 'TPI Douala', ?, 'INC-1') RETURNING id", Long.class, reference, clientId);
    }

    @Test
    void une_alerte_a_un_juriste_met_Outlook_en_file_avec_la_reference_du_dossier() {
        Utilisateur juriste = utilisateur("kc-notif-juriste", "JURISTE", "juriste.test@afrilandfirstbank.com");
        Long dossierId = dossier("DOS-NOTIF-1");

        Alerte alerte = alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, dossierId, juriste,
                "[audienceId:5;J-7] Audience prévue (RG-AUD-03) — motif interne");

        List<NotificationEnvoi> envois = notificationEnvoiRepository.findAll();
        assertThat(envois).hasSize(1);
        NotificationEnvoi outlook = envois.get(0);
        assertThat(outlook.getEventId()).isEqualTo("alerte-" + alerte.getId() + ":EMAIL");
        assertThat(outlook.getCanal()).isEqualTo(CanalEngine.EMAIL);
        assertThat(outlook.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(outlook.getDestinataire()).isEqualTo("juriste.test@afrilandfirstbank.com");
        assertThat(outlook.getSujet()).isEqualTo("[Audiences et délibérés] Rappel d'audience - DOS-NOTIF-1");
        // Le texte de l'alerte applicative ne sort pas : ni marqueur, ni code, ni motif.
        assertThat(outlook.getCorps()).doesNotContain("audienceId", "RG-AUD", "motif interne")
                .contains("/dossiers/" + dossierId);
    }

    @Test
    void une_alerte_a_un_avocat_avec_compte_ne_met_rien_en_file() {
        Utilisateur avocat = utilisateur("kc-notif-avocat", "AVOCAT", "avocat@cabinet.cm");

        alerteService.declencher(TypeAlerte.PAIEMENT_EFFECTUE, dossier("DOS-NOTIF-2"), avocat, "Paiement");

        assertThat(notificationEnvoiRepository.findAll()).isEmpty();
    }

    @Test
    void outlook_coupe_par_le_DJ_rien_ne_part() {
        jdbc.update("UPDATE configuration_systeme SET valeur = 'APPLICATIF' WHERE cle = 'CANAUX_NOTIFICATION_DEFAUT'");
        Utilisateur dj = utilisateur("kc-notif-dj", "DJ", "dj@afrilandfirstbank.com");

        alerteService.declencher(TypeAlerte.CHARGE_MAX_AVOCAT, null, dj, "Charge");

        assertThat(notificationEnvoiRepository.findAll()).isEmpty();
    }

    /** D12 : l'action annulée emporte sa notification ; rien ne partira pour un événement qui n'a pas eu lieu. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void une_action_annulee_emporte_ses_notifications() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(statut -> {
            Utilisateur juriste = utilisateur("kc-notif-annule", "JURISTE", "juriste@afrilandfirstbank.com");
            alerteService.declencher(TypeAlerte.DOCUMENT_CHARGE, dossier("DOS-NOTIF-3"), juriste, "Document");
            assertThat(notificationEnvoiRepository.count()).isEqualTo(1);
            statut.setRollbackOnly();
        });

        assertThat(notificationEnvoiRepository.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerte", Long.class)).isZero();
    }
}
