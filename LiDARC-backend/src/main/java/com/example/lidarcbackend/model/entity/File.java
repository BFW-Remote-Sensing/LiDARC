package com.example.lidarcbackend.model.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "files")
@Getter
@Setter
@Builder
@AllArgsConstructor
public class File {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String filename;

  @Column(name = "creation_year")
  private Short creationYear;
  @Column(name = "size_bytes")
  private Long sizeBytes;
  @Column(name = "min_x")
  private Double minX;

  @Column(name = "min_y")
  private Double minY;
  @Column(name = "min_z")
  private Double minZ;
  @Column(name = "min_gpstime")
  private Double minGPSTime;
  @Column(name = "max_x")
  private Double maxX;
  @Column(name = "max_y")
  private Double maxY;
  @Column(name = "max_z")
  private Double maxZ;
  @Column(name = "max_gpstime")
  private Double maxGPSTime;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "coordinate_system")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private CoordinateSystem coordinateSystem;
  @Column(name = "las_version")
  private String lasVersion;
  @Column(name = "capture_software")
  private String captureSoftware;
  @Column(name = "uploaded")
  private Boolean uploaded;

  @Column(name = "uploaded_at", updatable = false)
  @CreationTimestamp //TODO change this to the actual upload time when implementing uploadFinished
  private Instant uploaded_at;

  @OneToMany(mappedBy = "file")
  private Set<Url> urls = new LinkedHashSet<>();

  public File() {

  }
}
