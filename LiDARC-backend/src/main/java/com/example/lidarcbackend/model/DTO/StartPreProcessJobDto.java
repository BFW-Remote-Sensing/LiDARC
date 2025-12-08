package com.example.lidarcbackend.model.DTO;

import com.example.lidarcbackend.api.comparison.dtos.GridParameters;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StartPreProcessJobDto {
    private String jobId;
    private MinioObjectDto file;
    private GridParameters grid;
}
