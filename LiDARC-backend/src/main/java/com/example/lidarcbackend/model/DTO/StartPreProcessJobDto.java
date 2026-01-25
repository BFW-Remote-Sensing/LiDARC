package com.example.lidarcbackend.model.DTO;

import com.example.lidarcbackend.api.comparison.dtos.GridParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class StartPreProcessJobDto {
    private String jobId;
    private MinioObjectDto file;
    private GridParameters grid;
    private List<BoundingBox> bboxes;
    private Long comparisonId;
    private Long fileId;
    private Integer pointFilterLowerBound;
    private Integer pointFilterUpperBound;
    private Boolean pointFilterEnabled;
    private Boolean outlierDetectionEnabled;
    private Double outlierDeviationFactor;
}
