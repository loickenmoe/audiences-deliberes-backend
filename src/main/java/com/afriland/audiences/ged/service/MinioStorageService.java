package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Intégration MinIO (RG-GED-02/06) : dépôt, URL pré-signée de récupération, suppression physique
 * de l'objet. Point d'entrée unique du stockage — seul {@link GedService} y fait appel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final int EXPIRATION_URL_MINUTES = 10;

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /** Dépose le fichier sous une clé unique {@code dossiers/{dossierId}/{uuid}-{nomFichier}} et la retourne. */
    public String deposer(Long dossierId, MultipartFile fichier) {
        String cle = "dossiers/" + dossierId + "/" + UUID.randomUUID() + "-" + fichier.getOriginalFilename();
        try (InputStream flux = fichier.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(cle)
                    .stream(flux, fichier.getSize(), -1)
                    .contentType(fichier.getContentType())
                    .build());
            return cle;
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        } catch (Exception e) {
            log.error("Échec du dépôt MinIO pour la clé {}", cle, e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCodes.ERREUR_INTERNE, "Échec du dépôt du document dans le stockage");
        }
    }

    /** URL pré-signée de téléchargement (FR-GED-02), valide {@value #EXPIRATION_URL_MINUTES} minutes. */
    public String urlTelechargement(String cheminStockage) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(cheminStockage)
                    .expiry(EXPIRATION_URL_MINUTES, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Échec de génération de l'URL pré-signée pour la clé {}", cheminStockage, e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCodes.ERREUR_INTERNE, "Échec de récupération du document depuis le stockage");
        }
    }

    /**
     * Suppression physique de l'objet — best-effort : un échec ne bloque jamais la suppression
     * logique en base ({@code document.est_supprime}), qui reste la source de vérité exposée par
     * l'API ; un objet orphelin résiduel dans MinIO est un problème d'exploitation, pas fonctionnel.
     */
    public void supprimer(String cheminStockage) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(cheminStockage).build());
        } catch (Exception e) {
            log.warn("Échec de la suppression MinIO de l'objet {} (suppression logique conservée) : {}",
                    cheminStockage, e.getMessage());
        }
    }
}
