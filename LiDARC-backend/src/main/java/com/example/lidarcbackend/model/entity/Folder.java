package com.example.lidarcbackend.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
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

  @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<File> files;

  @ColumnDefault("true")
  @Column(name = "active")
  private Boolean active;

  public Folder() {

  }

  public void addFile(File file) {
    if (file != null) {
      files.add(file);
      file.setFolder(this);
    }
  }
}
