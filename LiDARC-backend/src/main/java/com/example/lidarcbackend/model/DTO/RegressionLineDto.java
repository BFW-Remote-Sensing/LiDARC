package com.example.lidarcbackend.model.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class RegressionLineDto {
    private Double slope;
    private Double intercept;
    @JsonProperty("x_min")
    private Double xMin;
    @JsonProperty("x_max")
    private Double xMax;

    public Double getxMin() {
        return xMin;
    }

    public void setxMin(Double xMin) {
        this.xMin = xMin;
    }

    public Double getxMax() {
        return xMax;
    }

    public void setxMax(Double xMax) {
        this.xMax = xMax;
    }

    public Double getIntercept() {
        return intercept;
    }

    public Double getSlope() {
        return slope;
    }

    public void setSlope(Double slope) {
        this.slope = slope;
    }

    public void setIntercept(Double intercept) {
        this.intercept = intercept;
    }


}
