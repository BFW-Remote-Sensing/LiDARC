package com.example.lidarcbackend.api.comparison.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateComparisonRequest {
    private String name;

    private Boolean needHighestVegetation;

    private Boolean needOutlierDetection;

    private Boolean needStatisticsOverScenery;

    private Boolean needMostDifferences;

    private List<Long> fileMetadataIds;

    private GridParameters grid;
}
