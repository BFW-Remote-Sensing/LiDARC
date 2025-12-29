package com.example.lidarcbackend.model.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CreateReportDto {
    @NotBlank
    private String title;
    @NotNull
    private List<ReportComponentDto> components;
}
