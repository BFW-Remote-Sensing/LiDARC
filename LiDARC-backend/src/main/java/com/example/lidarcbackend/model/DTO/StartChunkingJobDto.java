package com.example.lidarcbackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class StartChunkingJobDto {
    private Long comparisonId;
    private Integer chunkingSize;
    private MinioObjectDto file;
}
