package com.afriland.audiences.ged.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client MinIO (M8, Épic 6) — bucket applicatif créé au démarrage s'il n'existe pas encore
 * (`docker-compose.yml` ne provisionne qu'un serveur MinIO vierge, aucun bucket).
 * <p>
 * La vérification/création du bucket est faite via un {@link ApplicationRunner} distinct plutôt
 * qu'un {@code @PostConstruct} sur cette classe : ce dernier appellerait la méthode {@code @Bean}
 * {@link #minioClient()} pendant la propre construction de {@code MinioConfig}, provoquant une
 * référence circulaire (« Requested bean is currently in creation ») — constaté en validation
 * manuelle M8.
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner assurerBucketMinio(MinioClient minioClient) {
        return args -> {
            try {
                boolean existe = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!existe) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    log.info("Bucket MinIO '{}' créé", bucketName);
                }
            } catch (Exception e) {
                // Non bloquant au démarrage (ex. MinIO temporairement indisponible) : chaque appel
                // applicatif échouera explicitement en ApiException si le bucket manque réellement.
                log.warn("Impossible de vérifier/créer le bucket MinIO '{}' au démarrage : {}", bucketName, e.getMessage());
            }
        };
    }
}
