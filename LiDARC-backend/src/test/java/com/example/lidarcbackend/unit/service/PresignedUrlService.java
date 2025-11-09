package com.example.lidarcbackend.unit.service;

import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
public class PresignedUrlService {



  @Test
  void contextLoads() {
  }


}
