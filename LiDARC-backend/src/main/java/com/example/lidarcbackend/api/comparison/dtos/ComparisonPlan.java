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
    private List<ComparisonFile> filesToInclude = new ArrayList<>();
    private List<ComparisonFile> filesToExclude = new ArrayList<>();
    private List<StartPreProcessJobDto> jobsToStart = new ArrayList<>();

    public void addIncludedFile(ComparisonFile file, StartPreProcessJobDto job) {
        this.filesToInclude.add(file);
        this.jobsToStart.add(job);
    }

    public void addExcludedFile(ComparisonFile file) {
        this.filesToExclude.add(file);
    }

    public void merge(ComparisonPlan other) {
        this.filesToInclude.addAll(other.getFilesToInclude());
        this.filesToExclude.addAll(other.getFilesToExclude());
        this.jobsToStart.addAll(other.getJobsToStart());
    }
}
