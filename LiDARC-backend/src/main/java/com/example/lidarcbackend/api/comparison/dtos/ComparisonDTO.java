package com.example.lidarcbackend.api.comparison.dtos;

import com.example.lidarcbackend.api.folder.dtos.FolderDTO;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ComparisonDTO {
    private Long id;

    private String name;

    private Boolean needHighestVegetation;

    private Boolean needOutlierDetection;

    private Boolean needStatisticsOverScenery;

    private Boolean needMostDifferences;

    private LocalDateTime createdAt;

    private String status;

    private String latestReport;

    private String errorMessage;

    private GridParameters grid;

    private List<FileMetadataDTO> files;

    private String resultBucket;

    private String resultObjectKey;

    private FolderDTO folderA;

    private FolderDTO folderB;

    private Integer pointFilterLowerBound;

    private Integer pointFilterUpperBound;

    private Boolean needPointFilter;

    private Double outlierDeviationFactor;

    public ComparisonDTO() {

    }
}
