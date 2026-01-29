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
public class CorrelationMetricsDto {
    @JsonProperty("pearson_correlation")
    private Double pearsonCorrelation;
    @JsonProperty("regression_line")
    private RegressionLineDto regressionLine;
}
