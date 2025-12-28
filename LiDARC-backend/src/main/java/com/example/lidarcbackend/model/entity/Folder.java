package com.example.lidarcbackend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "folders")
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Folder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "status")
  private String status;

  @OneToMany(fetch = FetchType.EAGER) //possibly change to LAZY
  @JoinColumn(name = "folder_id")
  private List<File> files;

  public Folder() {

  }
}
