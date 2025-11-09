package com.example.lidarcbackend.unit.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
public abstract class AbstractRepositoryTest {
  static PostgreSQLContainer postgres;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry, PostgreSQLContainer postgres) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // or create-drop
  }

  @BeforeAll
  static void setup() {
     postgres =
        new PostgreSQLContainer("postgres:16-alpine");
    postgres.start();

  }


  @BeforeEach
  default void cleanDatabase() {


  }
}
