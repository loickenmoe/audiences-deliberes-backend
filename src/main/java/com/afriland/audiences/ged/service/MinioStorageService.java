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
 * Intégration MinIO (RG-GED-02/06) : dépôt, URL pré-signée de récupération, suppression physique
 * de l'objet. Point d'entrée unique du stockage — seul {@link GedService} y fait appel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final int EXPIRATION_URL_MINUTES = 10;

    private final MinioClient minioClient;

    /** Préfixe des pièces justificatives de frais (Q-97). Public : il sert de marqueur à la lecture. */
    public static final String PREFIXE_PIECES_FRAIS = "frais";

    @Value("${minio.bucket-name}")
    private String bucketName;

    /** Dépose le fichier sous une clé unique {@code dossiers/{dossierId}/{uuid}-{nomFichier}} et la retourne. */
    public String deposer(Long dossierId, MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            return deposer("dossiers/" + dossierId, fichier.getOriginalFilename(), fichier.getContentType(), flux, fichier.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        }
    }

    /**
     * Variante pour un document généré par le système (ex. lettre de constitution, M9), sans
     * passer par un {@link MultipartFile} d'upload HTTP.
     */
    public String deposer(Long dossierId, String nomFichier, String contentType, byte[] contenu) {
        return deposer("dossiers/" + dossierId, nomFichier, contentType, new ByteArrayInputStream(contenu), (long) contenu.length);
    }

    /**
     * Variante pour un fichier non rattaché à un dossier (ex. jurisprudence sans {@code dossierId},
     * Q-58, M12) — {@code document.dossier_id} étant `NOT NULL`, ce chemin ne peut jamais créer de
     * ligne `document` GED ; il ne fait que déposer l'octet-stream sous un préfixe dédié.
     */
    public String deposerSansDossier(MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            return deposer("jurisprudence", fichier.getOriginalFilename(), fichier.getContentType(), flux, fichier.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Fichier illisible : " + e.getMessage());
        }
    }

    /**
     * Pièce justificative d'une demande de frais (Q-97). Elle n'est pas un document GED : elle
     * n'est pas rattachée à une étape du dossier, n'est pas soumise au circuit de suppression, et
     * son cycle de vie est celui de la demande. D'où un préfixe dédié, que
     * {@code FraisService} reconnaît pour ne signer que ce qu'il a lui-même stocké.
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
     * Archive d'audit (Q-113), produite par le système et rattachée à aucun dossier.
     *
     * <p>Préfixe dédié : ces fichiers ne sont pas des documents GED — ils ne suivent ni le circuit
     * de suppression, ni le cycle de vie d'un dossier. Leur seule porte de sortie est l'endpoint de
     * téléchargement, qui trace chaque récupération.
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
