package com.example.lidarcbackend.model.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VegetationStatsDto {
    @JsonProperty("fileA_metrics")
    private FileMetricsDto itemAMetrics;
    @JsonProperty("fileB_metrics")
    private FileMetricsDto itemBMetrics;
    @JsonProperty("difference_metrics")
    private DifferenceMetricsDto differenceMetrics;
    @JsonProperty("group_mapping")
    private GroupMappingDto groupMapping;
}
