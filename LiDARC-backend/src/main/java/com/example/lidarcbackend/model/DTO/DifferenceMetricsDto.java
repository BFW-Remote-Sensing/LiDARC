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
public class DifferenceMetricsDto {
    private Double mean;
    private Double median;
    private Double std;
    @JsonProperty("most_negative")
    private Double mostNegative;
    @JsonProperty("least_negative")
    private Double leastNegative;
    @JsonProperty("smallest_positive")
    private Double smallestPositive;
    @JsonProperty("largest_positive")
    private Double largestPositive;
    private CorrelationMetricsDto correlation;
    private HistogramDto histogram;
}
