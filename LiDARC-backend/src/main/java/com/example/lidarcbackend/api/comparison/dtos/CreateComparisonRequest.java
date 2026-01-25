package com.example.lidarcbackend.api.comparison.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public class CreateComparisonRequest {
    @NotBlank
    private String name;

    private Boolean needHighestVegetation;

    private Boolean needOutlierDetection;

    private Boolean needStatisticsOverScenery;

    private Boolean needMostDifferences;

    private Long folderAId;

    private Long folderBId;

    //TODO: Should we remove that?
    private List<Long> fileMetadataIds;
    //TODO: ADD NOT EMPTY / NOT NULL TO EITHER
    private List<Long> folderAFiles;
    private List<Long> folderBFiles;

    private Integer pointFilterLowerBound;

    private Integer pointFilterUpperBound;

    private Boolean needPointFilter;

    private Double outlierDeviationFactor;

    @NotNull
    @Valid
    private GridParameters grid;
}
