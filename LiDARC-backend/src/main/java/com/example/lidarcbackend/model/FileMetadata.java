package com.example.lidarcbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "files")
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String filename;

    private Short creationYear;
    private Long sizeBytes;

    @Column(name = "min_x")
    private Double minX;
    @Column(name = "min_y")
    private Double minY;
    @Column(name = "min_z")
    private Double minZ;
    @Column(name = "min_gpstime")
    private Double minGpsTime;

    @Column(name = "max_x")
    private Double maxX;
    @Column(name = "max_y")
    private Double maxY;
    @Column(name = "max_z")
    private Double maxZ;

    @Column(name = "max_gpstime")
    private Double maxGpsTime;

    private Integer coordinateSystem;

    private String lasVersion;
    private String captureSoftware;

    private Boolean uploaded = false;

    private LocalDateTime uploadedAt;
}
