package com.example.lidarcbackend.service.comparisons;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.exception.ValidationException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IComparisonService {
    Page<ComparisonDTO> getPagedComparisons(Pageable pageable, String search);

    List<ComparisonDTO> getAllComparisons();

    ComparisonDTO saveComparison(CreateComparisonRequest comparison, List<Long> fileMetadataIds) throws NotFoundException, ValidationException;

    ComparisonDTO getComparison(Long comparisonId) throws NotFoundException;

    void startChunkingComparisonJob(Long comparisonId, int chunkSize) throws NotFoundException;

    void saveVisualizationComparison(Map<String, Object> result);

    Optional<Object> pollVisualizationResults(Long comparisonId);

    void processPreprocessingResult(Map<String, Object> result);

    void processComparisonResult(Map<String, Object> result);

    @Transactional
    void deleteComparisonById(Long id) throws NotFoundException;
}
