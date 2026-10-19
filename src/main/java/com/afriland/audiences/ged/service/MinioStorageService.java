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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO integration (RG-GED-02/06): upload, presigned download URL, physical deletion of the
 * object. Single entry point to storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final int EXPIRATION_URL_MINUTES = 10;

    private final MinioClient minioClient;

    /** Prefix for expense supporting documents. Public: it serves as a marker when reading. */
    public static final String PREFIXE_PIECES_FRAIS = "frais";

    @Value("${minio.bucket-name}")
    private String bucketName;

    /** Stores the file under a unique key {@code dossiers/{dossierId}/{uuid}-{nomFichier}} and returns it. */
    public String deposer(Long dossierId, MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            return deposer("dossiers/" + dossierId, fichier.getOriginalFilename(), fichier.getContentType(), flux, fichier.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        }
    }

    /**
     * Variant for a system-generated document (e.g. appointment letter), without an HTTP
     * {@link MultipartFile} upload.
     */
    public String deposer(Long dossierId, String nomFichier, String contentType, byte[] contenu) {
        return deposer("dossiers/" + dossierId, nomFichier, contentType, new ByteArrayInputStream(contenu), (long) contenu.length);
    }

    /**
     * Variant for a file not attached to a case file (e.g. case law without {@code dossierId}).
     * Since {@code document.dossier_id} is {@code NOT NULL}, this path never creates a GED
     * {@code document} row; it only stores the bytes under a dedicated prefix.
     */
    public String deposerSansDossier(MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            return deposer("jurisprudence", fichier.getOriginalFilename(), fichier.getContentType(), flux, fichier.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        }
    }

    /**
     * Supporting document of an expense request. It is not a GED document: it is not attached to a
     * case file step, does not go through the deletion workflow, and shares the request's lifecycle.
     * Hence a dedicated prefix, which {@code FraisService} recognises so it only signs what it stored.
     */
    public String deposerPieceFrais(MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            return deposer(PREFIXE_PIECES_FRAIS, fichier.getOriginalFilename(), fichier.getContentType(),
                    flux, fichier.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        }
    }

    /**
     * Audit archive, produced by the system and attached to no case file.
     *
     * <p>Dedicated prefix: these files are not GED documents and follow neither the deletion workflow
     * nor a case file lifecycle. Their only way out is the download endpoint, which logs every retrieval.
     */
    public String deposerArchiveAudit(String nomFichier, String contentType, byte[] contenu) {
        return deposer("audit", nomFichier, contentType, new ByteArrayInputStream(contenu), (long) contenu.length);
    }

    private String deposer(String prefixe, String nomFichier, String contentType, InputStream flux, long taille) {
        String cle = prefixe + "/" + UUID.randomUUID() + "-" + nomFichier;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(cle)
                    .stream(flux, taille, -1)
                    .contentType(contentType)
                    .build());
            return cle;
        } catch (Exception e) {
            log.error("Échec du dépôt MinIO pour la clé {}", cle, e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCodes.ERREUR_INTERNE, "Échec du dépôt du document dans le stockage");
        }
    }

    /** Presigned download URL (FR-GED-02), valid for {@value #EXPIRATION_URL_MINUTES} minutes. */
    /**
     * Object content, to upload it elsewhere: currently to the Notification Engine, which requires
     * the file to be supplied through a single-use URL.
     */
    public byte[] lire(String cheminStockage) {
        try (var flux = minioClient.getObject(io.minio.GetObjectArgs.builder()
                .bucket(bucketName)
                .object(cheminStockage)
                .build())) {
            return flux.readAllBytes();
        } catch (Exception e) {
            log.error("Lecture impossible de l'objet {}", cheminStockage, e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCodes.ERREUR_INTERNE, "Échec de lecture du document depuis le stockage");
        }
    }

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
     * Physical deletion of the object, best-effort: a failure never blocks the logical deletion in the
     * database ({@code document.est_supprime}), which remains the source of truth exposed by the API.
     * A leftover orphan object in MinIO is an operations issue, not a functional one.
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
