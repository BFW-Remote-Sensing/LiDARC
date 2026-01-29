package com.example.lidarcbackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class PercentilesDto {
    private Double p10;
    private Double p25;
    private Double p50;
    private Double p75;
    private Double p90;
}
