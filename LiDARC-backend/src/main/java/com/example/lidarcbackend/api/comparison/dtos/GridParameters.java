package com.example.lidarcbackend.api.comparison.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class GridParameters {
    Integer cellWidth;
    Integer cellHeight;
    Double xMin;
    Double xMax;
    Double yMin;
    Double yMax;
}
