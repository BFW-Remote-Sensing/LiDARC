package com.example.lidarcbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
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

    @Column(name = "capture_year")
    private Short captureYear;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "min_x")
    private Double minX;
    @Column(name = "min_y")
    private Double minY;
    @Column(name = "min_z")
    private Double minZ;

    @Column(name = "max_x")
    private Double maxX;
    @Column(name = "max_y")
    private Double maxY;
    @Column(name = "max_z")
    private Double maxZ;

    @Column(name = "system_identifier")
    private String systemIdentifier;

    @Column(name = "coordinate_system")
    private Integer coordinateSystem;

    @Column(name = "las_version")
    private String lasVersion;

    @Column(name = "capture_software")
    private String captureSoftware;

    @Column(name = "point_count")
    private Long pointCount;

    @Column(name = "file_creation_date")
    private LocalDate fileCreationDate;

    private Boolean uploaded = false;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;


}
