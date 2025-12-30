package com.example.lidarcbackend.api.comparison.dtos;

import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;

import java.util.List;

public record PreProcessJobsReadyEvent(List<StartPreProcessJobDto> jobsToStart) {
}
