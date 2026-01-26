package com.example.lidarcbackend.model.DTO;

import com.example.lidarcbackend.api.comparison.dtos.GridParameters;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StartPreProcessJobDto {
    private String jobId;
    private MinioObjectDto file;
    private GridParameters grid;
    private List<BoundingBox> bboxes;
    private Long comparisonId;
    private Long fileId;
    private Double individualPercentile;
    private Integer pointFilterLowerBound;
    private Integer pointFilterUpperBound;
    private Boolean pointFilterEnabled;
    private Boolean outlierDetectionEnabled;
    private Double outlierDeviationFactor;
}
