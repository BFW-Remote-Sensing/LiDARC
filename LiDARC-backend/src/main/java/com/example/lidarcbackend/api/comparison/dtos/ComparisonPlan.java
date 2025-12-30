package com.example.lidarcbackend.api.comparison.dtos;

import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComparisonPlan {
    private List<ComparisonFile> filesToSave = new ArrayList<>();
    private List<StartPreProcessJobDto> jobsToStart = new ArrayList<>();

    public void add(ComparisonFile file, StartPreProcessJobDto job) {
        this.filesToSave.add(file);
        this.jobsToStart.add(job);
    }

    public void merge(ComparisonPlan other) {
        this.filesToSave.addAll(other.getFilesToSave());
        this.jobsToStart.addAll(other.getJobsToStart());
    }
}
