package com.example.lidarcbackend.api.comparison.dtos;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class GridParameters {
    Integer cellWidth;
    Integer cellHeight;
    Double minX;
    Double maxX;
    Double minY;
    Double maxY;
}
