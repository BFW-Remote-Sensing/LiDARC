package com.example.lidarcbackend;

import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest
class LiDarcBackendApplicationTests {

  static org.testcontainers.postgresql.PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
      .withInitScript("init.sql");

  @Autowired
  public FileRepository fileRepository;
  @Autowired
  public UrlRepository urlRepository;
  @MockitoBean
  MinioClient minioClient;

  @BeforeAll
  static void setup() {
    postgres.start();
  }

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    postgres.close();
    postgres.start();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // or create-drop
  }


  @Test
  void contextLoads() {
  }
}
