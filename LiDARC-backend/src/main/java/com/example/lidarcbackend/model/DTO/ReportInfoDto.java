package com.example.lidarcbackend.model.DTO;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportInfoDto {
    private Long id;
    private String title;
    private String fileName;
    private Instant creationDate;
    private Long comparisonId;
}
