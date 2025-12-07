package com.example.lidarcbackend.api.comparison.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ComparisonResponse {
    private List<ComparisonDTO> items;
    private long totalItems;
    private int page;
    private int size;
}