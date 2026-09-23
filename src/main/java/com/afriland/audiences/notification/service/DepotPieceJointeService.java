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
 * Dépôt du fichier chez le Notification Engine, deuxième temps du mécanisme « claim-check »
 * (Guide V1 §3 bis) : le moteur a rendu une adresse à usage unique, le fichier y est envoyé
 * directement, et la notification ne portera ensuite que la clé de stockage.
 * <p>
 * <b>Aucun octet ne passe par Kafka ni par le moteur</b> : c'est tout l'intérêt du mécanisme, et la
 * raison pour laquelle une lettre de constitution de plusieurs centaines de kilo-octets peut être
 * jointe sans se heurter à la taille maximale d'un message.
 * <p>
 * <b>Un échec de dépôt ne retient pas la notification</b> : elle part sans son document, avec la
 * cause enregistrée. Prévenir sans la pièce vaut mieux que ne pas prévenir.
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
            // Réponse d'un autre service, ou ligne déjà traitée : rien à faire.
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
