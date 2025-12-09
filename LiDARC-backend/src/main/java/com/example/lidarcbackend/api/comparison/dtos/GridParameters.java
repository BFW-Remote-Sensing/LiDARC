package com.example.lidarcbackend.api.comparison.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class GridParameters {

    @JsonProperty("cellWidth")
    private Integer cellWidth;

    @JsonProperty("cellHeight")
    private Integer cellHeight;

    @JsonProperty("xMin")
    private Double xMin;

    @JsonProperty("xMax")
    private Double xMax;

    @JsonProperty("yMin")
    private Double yMin;

    @JsonProperty("yMax")
    private Double yMax;
}
