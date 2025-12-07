package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.ComparisonFilePK;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ComparisonService implements IComparisonService {
    private final ComparisonRepository comparisonRepository;
    private final ComparisonFileRepository comparisonFileRepository;
    private final IMetadataService metadataService;
    private final Validator validator;
    private final ComparisonMapper mapper;
    private final MetadataMapper metadataMapper;

    public ComparisonService(
            ComparisonRepository comparisonRepository,
            ComparisonFileRepository comparisonFileRepository,
            IMetadataService metadataService,
            Validator validator,
            ComparisonMapper mapper,
            MetadataMapper metadataMapper) {
        this.comparisonRepository = comparisonRepository;
        this.comparisonFileRepository = comparisonFileRepository;
        this.metadataService = metadataService;
        this.validator = validator;
        this.mapper = mapper;
        this.metadataMapper = metadataMapper;
    }

    @Override
    public Page<ComparisonDTO> getPagedComparisons(Pageable pageable) {
        Page<Comparison> comparisonPage = comparisonRepository.findAll(pageable);

        List<ComparisonDTO> dtoList = comparisonPage.getContent().stream().map(comparison -> {
            ComparisonDTO dto = mapper.toDto(comparison);

            List<Long> fileMetadataIds = comparisonFileRepository
                    .getComparisonFilesByComparisonId(comparison.getId());

            List<String> fileMetadataIdStrings = fileMetadataIds.stream()
                    .map(String::valueOf)
                    .toList();

            List<FileMetadataDTO> fileMetadataDTOs = metadataService
                    .getMetadataList(fileMetadataIdStrings);

            dto.setFiles(fileMetadataDTOs);
            return dto;
        }).toList();

        return new PageImpl<>(dtoList, pageable, comparisonPage.getTotalElements());
    }


    @Override
    public List<ComparisonDTO> getAllComparisons() {
        List<Comparison> comparisons = comparisonRepository.findAll();

        return comparisons.stream().map(comparison -> {
            ComparisonDTO dto = mapper.toDto(comparison);

            List<Long> fileMetadataIds = comparisonFileRepository
                    .getComparisonFilesByComparisonId(comparison.getId());

            List<String> fileMetadataIdStrings = fileMetadataIds.stream()
                    .map(String::valueOf)
                    .toList();

            List<FileMetadataDTO> fileMetadataDTOs = metadataService
                    .getMetadataList(fileMetadataIdStrings);

            dto.setFiles(fileMetadataDTOs);
            return dto;
        }).toList();
    }


    @Override
    @Transactional
    public ComparisonDTO saveComparison(CreateComparisonRequest comparisonRequest, List<Long> fileMetadataIds) {
        // 1️⃣ Save the Comparison entity
        Comparison savedComparison = comparisonRepository.save(mapper.toEntityFromRequest(comparisonRequest));

        // 2️⃣ Save the ComparisonFile relations
        List<ComparisonFile> comparisonFiles = fileMetadataIds.stream()
                .map(fileId -> {
                    ComparisonFile cf = new ComparisonFile();
                    cf.setComparisonId(savedComparison.getId());
                    cf.setFileId(fileId);
                    return cf;
                })
                .toList();

        comparisonFileRepository.saveAll(comparisonFiles);

        // 3️⃣ Map the saved entity to DTO
        ComparisonDTO dto = mapper.toDto(savedComparison);

        // 4️⃣ Optionally, also populate the file metadata DTOs if you want them included
        List<FileMetadataDTO> fileMetadataDTOs = metadataService.getMetadataList(
                fileMetadataIds.stream().map(String::valueOf).toList()
        );
        dto.setFiles(fileMetadataDTOs);

        return dto;
    }


    @Override
    public ComparisonDTO GetComparison(Long comparisonId) {
        ComparisonDTO dto = comparisonRepository.findById(comparisonId).map(mapper::toDto).orElse(null);
        if (dto == null) return null;

        List<Long> fileMetadataIds = comparisonFileRepository.getComparisonFilesByComparisonId(comparisonId);
        List<String> fileMetadataIdStrings = fileMetadataIds.stream()
                .map(String::valueOf)
                .toList();
        List<FileMetadataDTO> fileMetadataDTOs = metadataService.getMetadataList(fileMetadataIdStrings);
        dto.setFiles(fileMetadataDTOs);
        return dto;
    }

    @Override
    @Transactional
    public void deleteComparisonById(Long id) {
            comparisonRepository.deleteById(id);
    }
}
