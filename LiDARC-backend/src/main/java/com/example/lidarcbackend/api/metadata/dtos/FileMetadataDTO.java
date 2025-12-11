package com.example.lidarcbackend.api.metadata.dtos;

import com.example.lidarcbackend.model.entity.CoordinateSystem;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class FileMetadataDTO {
    public FileMetadataDTO() {}
    private Long id;

    private String filename;

    private String originalFilename;

    private Short captureYear;

    private Long sizeBytes;
    private Double minX;

    private Double minY;
    private Double minZ;

    private Double maxX;
    private Double maxY;
    private Double maxZ;

    private String systemIdentifier;

    private String lasVersion;
    private String captureSoftware;
    private Boolean uploaded;
    private LocalDate fileCreationDate;
    private Long pointCount;
    private Instant uploadedAt;
    private String status;
}