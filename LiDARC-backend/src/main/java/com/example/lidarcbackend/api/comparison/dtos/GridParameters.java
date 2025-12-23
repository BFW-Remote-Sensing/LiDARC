package com.example.lidarcbackend.api.comparison.dtos;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class GridParameters {
    private Integer cellWidth;

    private Integer cellHeight;

    private Double xMin;

    private Double xMax;

    private Double yMin;

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
