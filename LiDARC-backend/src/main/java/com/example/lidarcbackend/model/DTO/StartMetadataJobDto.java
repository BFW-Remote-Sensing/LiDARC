package com.example.lidarcbackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StartMetadataJobDto {
    private String jobId;
    private String url;
    private String fileName;
}
