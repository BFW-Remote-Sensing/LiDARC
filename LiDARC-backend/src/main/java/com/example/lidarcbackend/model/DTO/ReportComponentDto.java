package com.example.lidarcbackend.model.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ReportComponentDto {
    @NotBlank
    private ReportType type; //"heatmap_single"
    private String fileName; //"img.png -> echarts .getImage?"
    private String title;
}
