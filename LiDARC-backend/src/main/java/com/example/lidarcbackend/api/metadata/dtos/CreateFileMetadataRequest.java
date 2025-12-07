package com.example.lidarcbackend.api.metadata.dtos;

import com.example.lidarcbackend.model.entity.CoordinateSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class CreateFileMetadataRequest {
    @NotBlank(message = "Filename cannot be blank")
    private String filename;

    private String originalFilename;

    @Positive(message = "Value must be greater than zero")
    private Short captureYear;

    @Positive(message = "Value must be greater than zero")
    private Long sizeBytes;

    private Double minX;

    private Double minY;
    private Double minZ;

    private Double maxX;
    private Double maxY;
    private Double maxZ;

    private String systemIdentifier;

    private CoordinateSystem coordinateSystem;
    private String lasVersion;
    private String captureSoftware;
    private Boolean uploaded;
    private LocalDate fileCreationDate;
    private Long pointCount;
    private Instant uploadedAt;
}
