package com.example.lidarcbackend.model.entity;

import io.minio.http.Method;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "urls")
public class Url {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JoinColumn(name = "file_id", nullable = false)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private File file;
  //TODO fetch correct base bucket from application properties

  @Column(name = "s3_bucket", nullable = false, columnDefinition = "TEXT default '" + "basebucket" +"'")
  private String bucket;
  @Column(name = "s3_url", nullable = false)
  private String presignedURL;
  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name= "method", nullable = false)
  @Enumerated(EnumType.STRING)
  private Method method;

}
