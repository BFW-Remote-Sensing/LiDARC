package com.example.lidarcbackend.model.DTO;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class CreateReportDto {
    @NotBlank
    private String title;
    @NotNull
    private List<ReportComponentDto> components;
    @Nullable
    private VegetationStatsDto stats;
}
