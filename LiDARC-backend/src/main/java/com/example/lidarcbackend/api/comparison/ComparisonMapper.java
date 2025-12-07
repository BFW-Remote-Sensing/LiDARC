package com.example.lidarcbackend.api.comparison;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.model.entity.Comparison;
import org.springframework.stereotype.Component;

@Component
public class ComparisonMapper {
    public ComparisonDTO toDto(Comparison entity) {
        if (entity == null) {
            return null;
        }
        ComparisonDTO dto = new ComparisonDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setNeedHighestVegetation(entity.getNeedHighestVegetation());
        dto.setNeedOutlierDetection(entity.getNeedOutlierDetection());
        dto.setNeedStatisticsOverScenery(entity.getNeedStatisticsOverScenery());
        dto.setNeedMostDifferences(entity.getNeedMostDifferences());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setStatus(entity.getStatus().toString());
        dto.setResultReportUrl(entity.getResultReportUrl());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }
    public Comparison toEntityFromRequest(CreateComparisonRequest request) {
        if(request == null){
            return null;
        }
        Comparison newComparison = new Comparison();
        newComparison.setName(request.getName());
        newComparison.setNeedHighestVegetation(request.getNeedHighestVegetation());
        newComparison.setNeedOutlierDetection(request.getNeedOutlierDetection());
        newComparison.setNeedStatisticsOverScenery(request.getNeedStatisticsOverScenery());
        newComparison.setNeedMostDifferences(request.getNeedMostDifferences());
        newComparison.setStatus(Comparison.Status.PENDING);
        newComparison.setCreatedAt(java.time.LocalDateTime.now());
        return newComparison;
    }
}
