package com.afriland.audiences.notification.service;

import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.ReponseDepotPieceJointe;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 12: uploading the file to the engine. A local server stands in for remote storage: the HTTP
 * behavior is what matters.
 */
@ExtendWith(MockitoExtension.class)
class DepotPieceJointeServiceTest {

    @Mock private NotificationEnvoiRepository repository;
    @Mock private MinioStorageService minioStorageService;

    private DepotPieceJointeService service;
    private HttpServer serveur;
    private final List<byte[]> depots = new CopyOnWriteArrayList<>();
    private int codeReponse = 200;

    @BeforeEach
    void demarrerLeStockage() throws IOException {
        service = new DepotPieceJointeService(repository, minioStorageService);
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/depot", echange -> {
            depots.add(echange.getRequestBody().readAllBytes());
            echange.sendResponseHeaders(codeReponse, -1);
            echange.close();
        });
        serveur.start();
    }

    @AfterEach
    void arreterLeStockage() {
        serveur.stop(0);
    }

    private String urlDepot() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort() + "/depot";
    }

    private NotificationEnvoi enAttenteDePieceJointe() {
        NotificationEnvoi envoi = NotificationEnvoi.aRemettre(
                new Origine("constitution-7-p9", "CONSTITUTION_VALIDEE", 5L, null, TypeDestinataire.AVOCAT, 9L),
                CanalEngine.EMAIL, "prestataire.test@example.cm", "Objet", "Texte");
        envoi.joindre(12L, "lettre-constitution-4.pdf", "application/pdf", "dossiers/5/lettre.pdf");
        envoi.attendreLaPieceJointe(LocalDateTime.now().plusMinutes(5));
        return envoi;
    }

    @Test
    void une_adresse_de_depot_fait_partir_le_fichier_puis_la_notification() {
        NotificationEnvoi envoi = enAttenteDePieceJointe();
        when(repository.findByEventId("constitution-7-p9-EMAIL")).thenReturn(Optional.of(envoi));
        when(minioStorageService.lire("dossiers/5/lettre.pdf")).thenReturn("%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        service.traiterLaReponse(new ReponseDepotPieceJointe("constitution-7-p9-EMAIL", "READY",
                "pending/abc/lettre-constitution-4.pdf", urlDepot(), null, null));

        // The file went to the returned address, without going through Kafka.
        assertThat(depots).hasSize(1);
        assertThat(new String(depots.get(0), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4");
        assertThat(envoi.getPieceJointeCle()).isEqualTo("pending/abc/lettre-constitution-4.pdf");
        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(envoi.getProchaineTentative()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    /** Notifying without the attachment beats not notifying: the notification is sent anyway. */
    @Test
    void un_stockage_qui_refuse_le_depot_ne_retient_pas_la_notification() {
        codeReponse = 403;
        NotificationEnvoi envoi = enAttenteDePieceJointe();
        when(repository.findByEventId("constitution-7-p9-EMAIL")).thenReturn(Optional.of(envoi));
        when(minioStorageService.lire("dossiers/5/lettre.pdf")).thenReturn("%PDF".getBytes(StandardCharsets.UTF_8));

        service.traiterLaReponse(new ReponseDepotPieceJointe("constitution-7-p9-EMAIL", "READY",
                "pending/abc/lettre.pdf", urlDepot(), null, null));

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(envoi.getPieceJointeCle()).isNull();
        assertThat(envoi.getPieceJointeChemin()).isNull();
        assertThat(envoi.getDerniereErreur()).startsWith("DEPOT_ECHEC");
    }

    @Test
    void une_demande_refusee_par_le_moteur_laisse_partir_la_notification_sans_document() {
        NotificationEnvoi envoi = enAttenteDePieceJointe();
        when(repository.findByEventId("constitution-7-p9-EMAIL")).thenReturn(Optional.of(envoi));

        service.traiterLaReponse(new ReponseDepotPieceJointe("constitution-7-p9-EMAIL", "FAILED", null, null,
                null, "Object storage temporarily unavailable"));

        assertThat(envoi.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(envoi.getDerniereErreur()).contains("DEPOT_REFUSE", "temporarily unavailable");
        assertThat(depots).isEmpty();
        verifyNoInteractions(minioStorageService);
    }

    /** The topic carries responses for all services: other services' responses are ignored. */
    @Test
    void une_reponse_pour_une_reference_inconnue_est_ignoree() {
        lenient().when(repository.findByEventId("autre-service")).thenReturn(Optional.empty());

        service.traiterLaReponse(new ReponseDepotPieceJointe("autre-service", "READY", "k", urlDepot(), null, null));

        assertThat(depots).isEmpty();
    }

    @Test
    void une_ligne_qui_n_attend_plus_rien_n_est_pas_retouchee() {
        NotificationEnvoi deja = enAttenteDePieceJointe();
        deja.pieceJointeDeposee("cle-precedente");
        when(repository.findByEventId("constitution-7-p9-EMAIL")).thenReturn(Optional.of(deja));

        service.traiterLaReponse(new ReponseDepotPieceJointe("constitution-7-p9-EMAIL", "READY", "cle-nouvelle",
                urlDepot(), null, null));

        assertThat(deja.getPieceJointeCle()).isEqualTo("cle-precedente");
        assertThat(depots).isEmpty();
    }
}
