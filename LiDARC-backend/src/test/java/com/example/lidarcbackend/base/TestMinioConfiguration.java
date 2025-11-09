package com.example.lidarcbackend.base;

import io.minio.MinioClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MinIOContainer;

@TestConfiguration
public class TestMinioConfiguration {
  private static final MinIOContainer MINIO = BucketBase.getMinIOContainer();

  static {
    // ensure container is started; Testcontainers manages lifecycle if annotated in tests,
    // but starting here is a simple approach for a TestConfiguration.
    if (!MINIO.isRunning()) {
      MINIO.start();
    }
  }

  @Bean
  public MinioClient minioClient() {
    String host = MINIO.getHost();
    Integer port = MINIO.getMappedPort(9000);
    String endpoint = "http://" + host + ":" + port;

    // Use the same credentials that the container exposes. Replace with actual values
    // you set on the container (example: "minioadmin"/"minioadmin").
    String accessKey = "minioadmin";
    String secretKey = "minioadmin";

    return MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
  }
}
