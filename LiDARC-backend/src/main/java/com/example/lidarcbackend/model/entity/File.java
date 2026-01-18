package com.example.lidarcbackend.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @Column(name = "original_filename")
    private String originalFilename;

    @Min(value = 1900, message = "capture_year must be >= 1900")
    @Max(value = 9999, message = "capture_year must be <= 9999")
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
    @Column(name = "file_creation_date")
    private LocalDate fileCreationDate;
    @Column(name = "point_count")
    private Long pointCount;
    @Column(name = "uploaded_at", updatable = false)
    @CreationTimestamp //TODO change this to the actual upload time when implementing uploadFinished
    private Instant uploadedAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private FileStatus status;

    @Column(name = "error_msg")
    private String errorMsg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @OneToMany(mappedBy = "file")
    private Set<Url> urls = new LinkedHashSet<>();

    public enum FileStatus {
        UPLOADING,
        UPLOADED,
        PROCESSING,
        PROCESSED,
        FAILED
    }

    public File() {

    }
}
