package com.example.lidarcbackend.api.comparison.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class GridParameters {
    @Min(value = 1, message = "Cell width must be at least 1 meter")
    private Integer cellWidth;
    @Min(value = 1, message = "Cell height must be at least 1 meter")
    private Integer cellHeight;
    @NotNull
    private Double xMin;
    @NotNull
    private Double xMax;
    @NotNull
    private Double yMin;
    @NotNull
    private Double yMax;

    public Integer getCellWidth() {
        return cellWidth;
    }

    public void setCellWidth(Integer cellWidth) {
        this.cellWidth = cellWidth;
    }

    public Integer getCellHeight() {
        return cellHeight;
    }

    public void setCellHeight(Integer cellHeight) {
        this.cellHeight = cellHeight;
    }

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

    public Double getyMax() {
        return yMax;
    }

    public void setyMax(Double yMax) {
        this.yMax = yMax;
    }

    public GridParameters(Integer cellWidth, Integer cellHeight, Double xMin, Double xMax, Double yMin, Double yMax) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }
}
