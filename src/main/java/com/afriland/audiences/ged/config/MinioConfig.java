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
 * MinIO client. The application bucket is created at startup if missing
 * ({@code docker-compose.yml} only provisions an empty MinIO server).
 * <p>
 * The bucket check runs in a separate {@link ApplicationRunner} rather than a {@code @PostConstruct}:
 * the latter would call the {@code @Bean} method {@link #minioClient()} while {@code MinioConfig} is
 * still being constructed, causing a circular reference.
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
                // Non-blocking at startup (e.g. MinIO temporarily unavailable): every application call
                // will fail explicitly with an ApiException if the bucket is really missing.
                log.warn("Impossible de vérifier/créer le bucket MinIO '{}' au démarrage : {}", bucketName, e.getMessage());
            }
        };
    }
}
