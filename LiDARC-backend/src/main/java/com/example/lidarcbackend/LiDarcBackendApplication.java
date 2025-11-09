package com.example.lidarcbackend;

import com.example.lidarcbackend.repository.FileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiDarcBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiDarcBackendApplication.class, args);
    }
}
