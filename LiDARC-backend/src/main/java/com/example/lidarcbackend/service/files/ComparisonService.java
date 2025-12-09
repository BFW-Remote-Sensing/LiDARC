package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.comparison.dtos.GridParameters;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.MinioObjectDto;
import com.example.lidarcbackend.model.DTO.StartComparisonJobDto;
import com.example.lidarcbackend.model.DTO.StartMetadataJobDto;
import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ComparisonService implements IComparisonService {
    private final ComparisonRepository comparisonRepository;
    private final ReportRepository reportRepository;
    private final ComparisonFileRepository comparisonFileRepository;
    private final FileRepository fileRepository;
    private final IMetadataService metadataService;
    private final Validator validator;
    private final RabbitTemplate rabbitTemplate;
    private final ComparisonMapper mapper;
    private final ObjectMapper objectMapper;
    private final MetadataMapper metadataMapper;
    private final WorkerStartService workerStartService;
    public ComparisonService(
        ComparisonRepository comparisonRepository,
        ComparisonFileRepository comparisonFileRepository, FileRepository fileRepository,
        IMetadataService metadataService,
        Validator validator,
        RabbitTemplate rabbitTemplate,
        ComparisonMapper mapper,
        ObjectMapper objectMapper,
        MetadataMapper metadataMapper,
        ReportRepository reportRepository,
        WorkerStartService workerStartService) {
        this.comparisonRepository = comparisonRepository;
        this.comparisonFileRepository = comparisonFileRepository;
        this.fileRepository = fileRepository;
        this.metadataService = metadataService;
        this.validator = validator;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.metadataMapper = metadataMapper;
        this.reportRepository = reportRepository;
        this.workerStartService = workerStartService;
    }

    @Override
    public Page<ComparisonDTO> getPagedComparisons(Pageable pageable) {
        Page<Comparison> comparisonPage = comparisonRepository.findAll(pageable);

        List<ComparisonDTO> dtoList = comparisonPage.getContent().stream().map(comparison -> {
            ComparisonDTO dto = mapper.toDto(comparison);
            reportRepository.findTopByComparisonIdOrderByCreationDateDesc(comparison.getId())
                    .ifPresent(report -> dto.setLatestReport("/reports/" + report.getId() + "/download"));
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
            reportRepository.findTopByComparisonIdOrderByCreationDateDesc(comparison.getId())
                    .ifPresent(report -> dto.setLatestReport("/reports/" + report.getId() + "/download"));

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
    public ComparisonDTO saveComparison(CreateComparisonRequest comparisonRequest, List<Long> fileMetadataIds) throws NotFoundException {
        //TODO: Is there validation needed?
        Comparison savedComparison = comparisonRepository.save(mapper.toEntityFromRequest(comparisonRequest));

        List<ComparisonFile> comparisonFiles = fileMetadataIds.stream()
                .map(fileId -> {
                    ComparisonFile cf = new ComparisonFile();
                    cf.setComparisonId(savedComparison.getId());
                    cf.setFileId(fileId);
                    return cf;
                })
                .toList();

        comparisonFileRepository.saveAll(comparisonFiles);

        Random r =  new Random();
        for (Long fileId : fileMetadataIds) {
            File toPreprocess = fileRepository.findById(fileId).orElseThrow(() -> new NotFoundException("File for comparison with id: " + fileId + " not found!"));
            //TODO: Change bucket on some var or column from db?
            MinioObjectDto file = new MinioObjectDto("basebucket", toPreprocess.getFilename());
            //TODO: Fix that later on
            String jobId = UUID.randomUUID().toString().substring(0, 5);
            StartPreProcessJobDto startPreProcessJobDto = new StartPreProcessJobDto(jobId, file, comparisonRequest.getGrid(), savedComparison.getId(), fileId);
            //TODO: Handle traceability in BE
            workerStartService.startPreprocessingJob(startPreProcessJobDto);
        }

        ComparisonDTO dto = mapper.toDto(savedComparison);

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
        reportRepository.findTopByComparisonIdOrderByCreationDateDesc(dto.getId())
                .ifPresent(report -> dto.setLatestReport("/reports/" + report.getId() + "/download"));
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

    @Override
    public void processPreprocessingResult(Map<String, Object> result) {
        log.info("Processing Preprocessing result...");

        String status = (String) result.get("status");
        String jobId = (String) result.get("job_id");
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");

        if (status == null || jobId == null || payload == null) {
            //Todo exception?
            log.error("Invalid result message received, missing jobId, payload or status");
            return;
        }

        Integer comparisonId = (Integer) payload.get("comparisonId");
        Integer fileId = (Integer) payload.get("fileId");
        if (comparisonId == null || fileId == null) {
            log.error("Missing comparisonId or fileId in preprocessing payload.");
            return;
        }

        if (!status.equalsIgnoreCase("success")) {
            //Todo set status to error + store error message?
            Object payloadMsg = ((Map<?, ?>) payload).get("msg");
            if (payloadMsg instanceof String errorMessage) {
                //Todo set status to error + store error message?
                log.warn("Preprocessing job {} failed: {}", jobId, errorMessage);
                return;
            }
        }

        Map<String, Object> resultObj = (Map<String, Object>) payload.get("result");
        if (resultObj == null) {
            log.error("Missing result object for file {}", fileId);
            return;
        }

        String bucket = (String) resultObj.get("bucket");
        String objectKey = (String) resultObj.get("objectKey");

        Optional<ComparisonFile> cfOpt = comparisonFileRepository.findComparisonFiles(comparisonId, fileId);
        if(cfOpt.isEmpty()){
            log.error("comparison_file entry not found for comparisonId={} fileId={}", comparisonId, fileId);
            return;
        }

        ComparisonFile cf = cfOpt.get();
        cf.setBucket(bucket);
        cf.setObjectKey(objectKey);
        comparisonFileRepository.save(cf);
        checkIfPreprocessingDoneAndStartComparison(comparisonId.longValue(), jobId);

    }

    @Override
    public void processComparisonResult(Map<String, Object> result) {

    }

    private void checkIfPreprocessingDoneAndStartComparison(Long comparisonId, String jobId) {
        List<ComparisonFile> comparisonFiles = comparisonFileRepository.findAllByComparisonId(comparisonId.intValue());

        boolean allReady = comparisonFiles.stream().allMatch(cf -> cf.getBucket() != null && cf.getObjectKey() != null);

        if(allReady) {
            log.info("Comparison {}: all preprocessing files contain bucket & objectKey. Starting comparison worker...", comparisonId);
            List<MinioObjectDto> filesDto = comparisonFiles.stream()
                    .map(cf -> new MinioObjectDto(cf.getBucket(), cf.getObjectKey()))
                    .toList();

            StartComparisonJobDto dto = new StartComparisonJobDto(
                    jobId,
                    comparisonId.toString(),
                    filesDto
            );

            workerStartService.startComparisonJob(dto);
        }
        else {
            log.info("Comparison {} is not ready yet. Waiting for other files.", comparisonId);
        }
    }


}
