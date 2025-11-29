package com.example.lidarcbackend;

import com.example.lidarcbackend.configuration.RabbitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({RabbitProperties.class}) //Bean has to be registered, here it is set explicitly
//Could also be done with @ConfigurationPropertiesScan on package configurations.
public class LiDarcBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(LiDarcBackendApplication.class, args);
  }
}
