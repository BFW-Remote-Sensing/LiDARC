package com.example.lidarcbackend.service.comparisons;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.*;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.ReportRepository;
import com.example.lidarcbackend.service.files.IMetadataService;
import com.example.lidarcbackend.service.files.WorkerStartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.units.qual.N;
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
        //TODO: Add validation
        Comparison savedComparison = comparisonRepository.save(mapper.toEntityFromRequest(comparisonRequest));

        //TODO: Change to work with multiple files...
        Set<Long> uniqueFileIds = new HashSet<>();
        if (fileMetadataIds != null) uniqueFileIds.addAll(fileMetadataIds);
        if (comparisonRequest.getFolderAFiles() != null) uniqueFileIds.addAll(comparisonRequest.getFolderAFiles());
        if (comparisonRequest.getFolderBFiles() != null) uniqueFileIds.addAll(comparisonRequest.getFolderBFiles());

        List<ComparisonFile> allFiles = new ArrayList<>();
        for (Long fileId : uniqueFileIds) {
            ComparisonFile comparisonFile = new ComparisonFile();
            comparisonFile.setComparisonId(savedComparison.getId());
            comparisonFile.setFileId(fileId);
            allFiles.add(comparisonFile);
        }
        comparisonFileRepository.saveAll(allFiles);

        if (comparisonRequest.getFolderAFiles() != null && !comparisonRequest.getFolderAFiles().isEmpty()) {
            processFolderGroup(comparisonRequest.getFolderAFiles(), comparisonRequest.getGrid(), savedComparison.getId(), "A");
        }
        if (comparisonRequest.getFolderBFiles() != null && !comparisonRequest.getFolderBFiles().isEmpty()) {
            processFolderGroup(comparisonRequest.getFolderBFiles(), comparisonRequest.getGrid(), savedComparison.getId(), "B");
        }
        if (fileMetadataIds != null && !fileMetadataIds.isEmpty()) {
            processFolderGroup(fileMetadataIds, comparisonRequest.getGrid(), savedComparison.getId(), "legacy");
        }
        ComparisonDTO dto = mapper.toDto(savedComparison);

        List<FileMetadataDTO> fileMetadataDTOs = metadataService.getMetadataList(
                uniqueFileIds.stream().map(String::valueOf).toList()
        );
        dto.setFiles(fileMetadataDTOs);

        return dto;
    }

    private void processFolderGroup(List<Long> fileIds, GridParameters grid, Long comparisonId, String groupName) throws NotFoundException {
        List<File> files = new ArrayList<>();
        for (Long fileId : fileIds) {
            files.add(fileRepository.findById(fileId)
                    .orElseThrow(() -> new NotFoundException("File for comparison with id: " + fileId + " not found!")));
        }

        List<BoundingBox> restrictedZones = new ArrayList<>();
        Random r =  new Random();
        for (File fileEntity : files) {
            BoundingBox rawBox = new BoundingBox(fileEntity.getMinX(), fileEntity.getMaxX(), fileEntity.getMinY(), fileEntity.getMaxY());
            BoundingBox snappedBox = snapToGrid(rawBox, grid);
            List<BoundingBox> validRegions = new ArrayList<>();
            validRegions.add(snappedBox);

            for (BoundingBox restrictedZone : restrictedZones) {
                validRegions = subtractRectangleFromList(validRegions, restrictedZone);
                if (validRegions.isEmpty()) break;
            }

            if (!validRegions.isEmpty()) {
                String uniqueJobId = String.format("c%d_%s_f%d_%s",
                        comparisonId,
                        groupName,
                        fileEntity.getId(),
                        UUID.randomUUID().toString().substring(0,8)
                );
                StartPreProcessJobDto startPreProcessJobDto = StartPreProcessJobDto.builder()
                        .jobId(uniqueJobId)
                        .grid(grid)
                        .bboxes(validRegions)
                        .comparisonId(comparisonId)
                        .file(new MinioObjectDto("basebucket", fileEntity.getFilename()))
                        .fileId(fileEntity.getId())
                        .build();
                workerStartService.startPreprocessingJob(startPreProcessJobDto);
                restrictedZones.add(snappedBox);
            }
            //TODO: Change bucket on some var or column from db?
            //TODO: Handle traceability in BE
            //TODO: Check if this is reliable
            //TODO: file1 , file2, file3, ...
        }
    }

    //TODO: Rethink that maybe, currently this implies that if one file already covers a small size of a cell that it will take the whole cell, and unsure if we want that?
    private BoundingBox snapToGrid(BoundingBox rawBox, GridParameters grid) {
        double cellW = grid.getCellWidth().doubleValue();
        double cellH = grid.getCellHeight().doubleValue();
        double gridOriginX = grid.getxMin();
        double gridOriginY = grid.getyMin();

        //Snap Min (FLOOR)
        double newXMin = gridOriginX + Math.floor((rawBox.getxMin() - gridOriginX) / cellW) * cellW;
        double newYMin = gridOriginY + Math.floor((rawBox.getyMin() - gridOriginY) / cellH) * cellH;

        double newXMax = gridOriginX + Math.ceil((rawBox.getxMax() - gridOriginX) / cellW) * cellW;
        double newYMax = gridOriginY + Math.ceil((rawBox.getyMax() - gridOriginY) / cellH) * cellH;
        return new BoundingBox(newXMin, newXMax, newYMin, newYMax);
    }

    private List<BoundingBox> subtractRectangleFromList(List<BoundingBox> currentRegions, BoundingBox obstacle) {
        List<BoundingBox> nextRegions = new ArrayList<>();
        for (BoundingBox region : currentRegions) {
            if (!intersects(region, obstacle)) {
                nextRegions.add(region);
            } else {
                nextRegions.addAll(shatterBox(region, obstacle));
            }
        }
        return nextRegions;
    }

    private List<BoundingBox> shatterBox(BoundingBox a, BoundingBox b) {
        List<BoundingBox> parts =  new ArrayList<>();

        double interXMin = Math.max(a.getxMin(), b.getxMin());
        double interXMax = Math.min(a.getxMax(), b.getxMax());
        double interYMin = Math.max(a.getyMin(), b.getyMin());
        double interYMax = Math.min(a.getyMax(), b.getyMax());

        if (a.getyMax() > interYMax) {
            parts.add(new BoundingBox(a.getxMin(), a.getxMax(), interYMax, a.getyMax()));
        }
        if (a.getyMin() < interYMin) {
            parts.add(new BoundingBox(a.getxMin(), a.getxMax(), a.getyMin(), interYMin));
        }
        if (a.getxMin() < interXMin) {
            parts.add(new BoundingBox(a.getxMin(), interXMin, interYMin, interYMax));
        }
        if (a.getxMax() > interXMax) {
            parts.add(new BoundingBox(interXMax, a.getxMax(), interYMin, interYMax));
        }
        return parts;
    }

    private boolean intersects(BoundingBox a, BoundingBox b) {
        return a.getxMin() < b.getxMax() && a.getxMax() > b.getxMin() &&
                a.getyMin() < b.getyMax() && a.getyMax() > b.getyMin();
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
        //TODO proper error handling

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
        if (comparisonId == null) {
            log.error("Missing comparisonId in preprocessing payload.");
            return;
        }

        Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId.longValue());
        if(comparisonOpt.isEmpty()){
            log.error("comparison file entry not found for comparisonId={}", comparisonId);
            return;
        }
        Comparison comparison = comparisonOpt.get();

        if (fileId == null) {
            log.error("Missing fileId in preprocessing payload.");
            persistComparisonError(comparison, "Missing fileId in preprocessing payload.");
            return;
        }


        if (!status.equalsIgnoreCase("success")) {
            Object payloadMsg = ((Map<?, ?>) payload).get("msg");
            if (payloadMsg instanceof String errorMessage) {
                log.warn("Preprocessing job {} failed: {}", jobId, errorMessage);
                persistComparisonError(comparison, errorMessage);
                return;
            }
        }

        Map<String, Object> resultObj = (Map<String, Object>) payload.get("result");
        if (resultObj == null) {
            log.error("Missing result object for file {}", fileId);
            persistComparisonError(comparison, "Missing result object for file " + fileId);
            return;
        }

        String bucket = (String) resultObj.get("bucket");
        String objectKey = (String) resultObj.get("objectKey");

        Optional<ComparisonFile> cfOpt = comparisonFileRepository.findComparisonFiles(comparisonId, fileId);
        if(cfOpt.isEmpty()){
            String errorMsg = String.format(
                    "comparison_file entry not found for comparisonId=%s fileId=%s",
                    comparisonId,
                    fileId
            );
            log.error(errorMsg);
            persistComparisonError(comparison, errorMsg);
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
        //TODO proper error handling
        log.info("Processing Comparison result...");

        String status = (String) result.get("status");
        String jobId = (String) result.get("job_id");
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");

        if (status == null || jobId == null || payload == null) {
            //Todo exception?
            log.error("Invalid result message received, missing jobId, payload or status");
            return;
        }

        Integer comparisonId = (Integer) payload.get("comparisonId");
        if (comparisonId == null) {
            log.error("Missing comparisonId in comparison payload.");
            return;
        }

        Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId.longValue());
        if(comparisonOpt.isEmpty()){
            log.error("comparison  entry not found for comparisonId={}", comparisonId);
            return;
        }
        Comparison comparison = comparisonOpt.get();


        if (!status.equalsIgnoreCase("success")) {
            Object payloadMsg = ((Map<?, ?>) payload).get("msg");
            if (payloadMsg instanceof String errorMessage) {
                log.warn("Comparison job {} failed: {}", jobId, errorMessage);
                persistComparisonError(comparison, errorMessage);
                return;
            }
        }

        Map<String, String> resultLocation = (Map<String, String>) payload.get("result");
        String bucket = resultLocation.get("bucket");
        String objectKey = resultLocation.get("objectKey");

        if(bucket == null || objectKey == null){
            log.error("Missing bucket or objectKey in payload.");
            persistComparisonError(comparison, "Missing bucket or objectKey in payload.");
            return;
        }

        comparison.setStatus(Comparison.Status.COMPLETED);
        comparison.setResultBucket(bucket);
        comparison.setResultObjectKey(objectKey);
        comparisonRepository.save(comparison);
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

    private void persistComparisonError(Comparison comparison, String errorMsg) {
        comparison.setStatus(Comparison.Status.FAILED);
        comparison.setErrorMessage(errorMsg);
        comparisonRepository.save(comparison);
    }
}
