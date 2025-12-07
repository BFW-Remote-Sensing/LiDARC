package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ComparisonService implements IComparisonService {
    private final ComparisonRepository comparisonRepository;
    private final ComparisonFileRepository comparisonFileRepository;
    private final IMetadataService metadataService;
    private final Validator validator;
    private final RabbitTemplate rabbitTemplate;
    private final ComparisonMapper mapper;
    private final ObjectMapper objectMapper;
    private final MetadataMapper metadataMapper;

    public ComparisonService(
            ComparisonRepository comparisonRepository,
            ComparisonFileRepository comparisonFileRepository,
            IMetadataService metadataService,
            Validator validator,
            RabbitTemplate rabbitTemplate,
            ComparisonMapper mapper,
            ObjectMapper objectMapper,
            MetadataMapper metadataMapper) {
        this.comparisonRepository = comparisonRepository;
        this.comparisonFileRepository = comparisonFileRepository;
        this.metadataService = metadataService;
        this.validator = validator;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
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
        List<Comparison> comparisons = comparisonRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

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

        // TODO Trigger worker for each file
//        Map<String, Object> grid = new HashMap<>();
//        grid.put("x_min", comparisonRequest.getGrid().getMinX());
//        grid.put("x_max", comparisonRequest.getGrid().getMaxX());
//        grid.put("y_min", comparisonRequest.getGrid().getMinY());
//        grid.put("y_max", comparisonRequest.getGrid().getMaxY());
//        grid.put("cell_height", comparisonRequest.getGrid().getCellHeight());
//        grid.put("cell_width", comparisonRequest.getGrid().getCellWidth());
//
//        Map<String, Object> msg = new HashMap<>();
//        // Use worker's expected jobId key
//        msg.put("jobId", UUID.randomUUID().toString());
//        Long fileId = comparisonRequest.getFileMetadataIds().getFirst();
//        // TODO get url for the fileId and send it
//        msg.put("url", "");
//
//        msg.put("grid", grid);
//
//        String exchange = System.getenv().getOrDefault("EXCHANGE_NAME", "worker.job");
//        String routingKey = "job.preprocessor.create";
//
//        String payload = objectMapper.writeValueAsString(msg);
//        rabbitTemplate.convertAndSend(exchange, routingKey, payload);


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
