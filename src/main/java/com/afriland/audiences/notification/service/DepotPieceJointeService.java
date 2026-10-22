package com.afriland.audiences.notification.service;

import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.ReponseDepotPieceJointe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Uploads the file to the Notification Engine, second step of the claim-check pattern (Guide V1 §3 bis):
 * the file goes straight to the single-use URL and the notification only carries the storage key, so
 * large files avoid Kafka's message size limit.
 * <p>
 * An upload failure does not hold the notification back: it is sent without the document and the
 * cause is recorded. Notifying without the attachment beats not notifying.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepotPieceJointeService {

    private static final Duration DELAI = Duration.ofSeconds(30);

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final MinioStorageService minioStorageService;

    @Transactional
    public void traiterLaReponse(ReponseDepotPieceJointe reponse) {
        if (reponse == null || reponse.requestId() == null) {
            return;
        }
        NotificationEnvoi envoi = notificationEnvoiRepository.findByEventId(reponse.requestId()).orElse(null);
        if (envoi == null || envoi.getStatut() != StatutEnvoi.ATTENTE_PIECE_JOINTE) {
            // Response for another service, or row already processed.
            return;
        }
        if (!reponse.prete()) {
            log.warn("Dépôt refusé pour {} : {}", reponse.requestId(), reponse.errorMessage());
            envoi.pieceJointeAbandonnee("DEPOT_REFUSE:" + reponse.errorMessage());
            return;
        }
        try {
            deposer(reponse.presignedPutUrl(), envoi.getPieceJointeChemin(), envoi.getPieceJointeMime());
            envoi.pieceJointeDeposee(reponse.storageKey());
            log.info("Pièce jointe déposée pour {} ({})", envoi.getEventId(), envoi.getPieceJointeNom());
        } catch (RuntimeException e) {
            log.error("Dépôt impossible pour {} : la notification partira sans sa pièce jointe",
                    envoi.getEventId(), e);
            envoi.pieceJointeAbandonnee("DEPOT_ECHEC:" + e.getMessage());
        }
    }

    private void deposer(String urlPresignee, String cheminLocal, String typeMime) {
        byte[] contenu = minioStorageService.lire(cheminLocal);
        HttpRequest requete = HttpRequest.newBuilder(URI.create(urlPresignee))
                .header("Content-Type", typeMime == null ? "application/octet-stream" : typeMime)
                .timeout(DELAI)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(contenu))
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(DELAI).build()) {
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() >= 300) {
                throw new IllegalStateException("Le stockage du moteur a refusé le dépôt : HTTP "
                        + reponse.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dépôt interrompu", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Dépôt impossible : " + e.getMessage(), e);
        }
    }
}
