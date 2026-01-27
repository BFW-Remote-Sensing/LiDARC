package com.example.lidarcbackend.model.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class FileMetricsDto {
    @JsonProperty("mean_veg_height")
    private Double meanVegHeight;
    @JsonProperty("median_veg_height")
    private Double medianVegHeight;
    @JsonProperty("std_veg_height")
    private Double stdVegHeight;
    @JsonProperty("min_veg_height")
    private Double minVegHeight;
    @JsonProperty("max_veg_height")
    private Double maxVegHeight;
    private PercentilesDto percentiles;
    @JsonProperty("mean_points_per_grid_cell")
    private Double meanPointsPerGridCell;
}
