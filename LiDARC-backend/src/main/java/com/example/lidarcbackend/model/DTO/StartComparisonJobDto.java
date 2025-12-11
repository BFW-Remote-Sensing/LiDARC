package com.example.lidarcbackend.model.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class StartComparisonJobDto {
    @NotNull
    private String jobId;

    @NotNull
    private String comparisonId;

    private List<MinioObjectDto> files;
}
