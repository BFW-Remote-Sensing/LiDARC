package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.entity.Comparison;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IComparisonService {
    Page<ComparisonDTO> getPagedComparisons(Pageable pageable);

    List<ComparisonDTO> getAllComparisons();

    ComparisonDTO saveComparison(CreateComparisonRequest comparison, List<Long> fileMetadataIds) throws NotFoundException;

    ComparisonDTO GetComparison(Long comparisonId);

    @Transactional
    void deleteComparisonById(Long id);
}
