package com.example.lidarcbackend;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.repository.UrlRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTests {
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
      .withInitScript("init.sql");

  @Autowired
  public FileRepository fileRepository;
  @Autowired
  public UrlRepository urlRepository;
  @Autowired
  public FolderRepository folderRepository;

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


  @BeforeEach
  void cleanDatabase() {
    fileRepository.deleteAll();
    urlRepository.deleteAll();
  }

  @Test
  void contextLoads() {
  }
}
