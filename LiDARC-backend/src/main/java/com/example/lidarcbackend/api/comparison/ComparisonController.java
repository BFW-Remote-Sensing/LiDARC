package com.example.lidarcbackend.api.comparison;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.ComparisonRequest;
import com.example.lidarcbackend.api.comparison.dtos.ComparisonResponse;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.metadata.dtos.MetadataRequest;
import com.example.lidarcbackend.api.metadata.dtos.MetadataResponse;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.service.files.ComparisonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/api/comparisons")
public class ComparisonController {
    private final ComparisonService comparisonService;

    @Autowired
    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<ComparisonDTO>> getAllComparisons() {
        List<ComparisonDTO> comparisons = comparisonService.getAllComparisons();
        return ResponseEntity.ok(comparisons);
    }

    @GetMapping("/paged")
    public ResponseEntity<ComparisonResponse> getPagedMetadata(@Valid @ModelAttribute ComparisonRequest request) {
        Sort sort = request.isAscending() ?
                Sort.by(request.getSortBy()).ascending() :
                Sort.by(request.getSortBy()).descending();

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Page<ComparisonDTO> result = comparisonService.getPagedComparisons(pageable);
        ComparisonResponse response = new ComparisonResponse(
                result.getContent(),
                result.getTotalElements(),
                result.getNumber(),
                request.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComparisonDTO> getComparison(@PathVariable Long id) {
        ComparisonDTO dto = comparisonService.GetComparison(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<ComparisonDTO> saveComparison(
            @RequestBody CreateComparisonRequest request
    ) {
        ComparisonDTO saved = comparisonService.saveComparison(request, request.getFileMetadataIds());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComparison(@PathVariable Long id) {
        comparisonService.deleteComparisonById(id);
        return ResponseEntity.noContent().build();
    }
}
