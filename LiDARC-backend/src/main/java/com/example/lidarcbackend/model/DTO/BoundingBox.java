package com.example.lidarcbackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class BoundingBox {
    private Double xMin;

    private Double xMax;

    private Double yMin;

    private Double yMax;
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

    public Double getyMin() {
        return yMin;
    }

    public void setyMin(Double yMin) {
        this.yMin = yMin;
    }

    public void setyMax(Double yMax) {
        this.yMax = yMax;
    }

    public Double getyMax() {
        return yMax;
    }
}
