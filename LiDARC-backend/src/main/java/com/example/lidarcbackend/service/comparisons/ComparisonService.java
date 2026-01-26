package com.example.lidarcbackend.service.comparisons;

import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.*;
import com.example.lidarcbackend.api.folder.dtos.FolderDTO;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.exception.ValidationException;
import com.example.lidarcbackend.model.DTO.*;
import com.example.lidarcbackend.model.JobType;
import com.example.lidarcbackend.model.TrackedJob;
import com.example.lidarcbackend.model.entity.*;
import com.example.lidarcbackend.repository.*;
import com.example.lidarcbackend.repository.projection.FileUsageCount;
import com.example.lidarcbackend.repository.projection.FolderUsageCount;
import com.example.lidarcbackend.service.IJobTrackingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.lidarcbackend.service.files.IMetadataService;
import com.example.lidarcbackend.service.files.WorkerStartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
  import java.util.stream.Stream;


@Service
@Slf4j
public class ComparisonService implements IComparisonService {
    private final ComparisonRepository comparisonRepository;
    private final ReportRepository reportRepository;
    private final ComparisonFileRepository comparisonFileRepository;
    private final ComparisonFolderRepository comparisonFolderRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final IMetadataService metadataService;
    private final IJobTrackingService jobTrackingService;
    private final Validator validator;
    private final RabbitTemplate rabbitTemplate;
    private final ComparisonMapper mapper;
    private final ObjectMapper objectMapper;
    private final MetadataMapper metadataMapper;
    private final WorkerStartService workerStartService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChunkingResultCacheService chunkingCacheService;
    private final MinioClient minioClient;
    protected final MinioProperties minioProperties;

    public ComparisonService(
            ComparisonRepository comparisonRepository,
            ComparisonFileRepository comparisonFileRepository,
            ComparisonFolderRepository comparisonFolderRepository,
            FileRepository fileRepository,
            FolderRepository folderRepository,
            IMetadataService metadataService,
            IJobTrackingService jobTrackingService,
            Validator validator,
            RabbitTemplate rabbitTemplate,
            ComparisonMapper mapper,
            ObjectMapper objectMapper,
            MetadataMapper metadataMapper,
            ReportRepository reportRepository,
            WorkerStartService workerStartService,
            ApplicationEventPublisher eventPublisher,
            MinioClient minioClient,
            MinioProperties minioProperties,
            ChunkingResultCacheService chunkingCacheService
    ) {

        this.comparisonRepository = comparisonRepository;
        this.comparisonFileRepository = comparisonFileRepository;
        this.comparisonFolderRepository = comparisonFolderRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.metadataService = metadataService;
        this.jobTrackingService = jobTrackingService;
        this.validator = validator;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.metadataMapper = metadataMapper;
        this.reportRepository = reportRepository;
        this.workerStartService = workerStartService;
        this.eventPublisher = eventPublisher;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.chunkingCacheService = chunkingCacheService;
    }

    @Override
    public Page<ComparisonDTO> getPagedComparisons(Pageable pageable, String search) {
        Page<Comparison> comparisonPage;

        if (search == null || search.isBlank()) {
            comparisonPage = comparisonRepository.findAll(pageable);
        } else {
            comparisonPage = comparisonRepository.findByNameContainingIgnoreCase(
                    search,
                    pageable
            );
        }
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
    public ComparisonDTO saveComparison(CreateComparisonRequest comparisonRequest, List<Long> fileMetadataIds) throws NotFoundException, ValidationException {
        //TODO: Add validation
        validateGrid(comparisonRequest);

        Comparison savedComparison = comparisonRepository.save(mapper.toEntityFromRequest(comparisonRequest));
        ComparisonPlan fullPlan = new ComparisonPlan();

        if (comparisonRequest.getFolderAFiles() != null && !comparisonRequest.getFolderAFiles().isEmpty()) {
            File firstFile = fileRepository.findById(comparisonRequest.getFolderAFiles().getFirst()).orElseThrow(() ->
                    new NotFoundException("File for comparison with id: " + comparisonRequest.getFolderAFiles().getFirst() + " not found!"));
            //TODO test if works for file to file comparisons
            String groupName;
            if (firstFile.getFolder() != null) {
                groupName = firstFile.getFolder().getName();
            } else {
                groupName = firstFile.getOriginalFilename();
            }
            fullPlan.merge(processFolderGroup(comparisonRequest.getFolderAFiles(), comparisonRequest.getGrid(),
                    savedComparison, groupName, savedComparison.getPointFilterLowerBound(),
                    savedComparison.getPointFilterUpperBound(), savedComparison.getNeedOutlierDetection(),
                    savedComparison.getOutlierDeviationFactor(), savedComparison.getNeedPointFilter()));
        }
        if (comparisonRequest.getFolderBFiles() != null && !comparisonRequest.getFolderBFiles().isEmpty()) {
            File firstFile = fileRepository.findById(comparisonRequest.getFolderBFiles().getFirst()).orElseThrow(() ->
                    new NotFoundException("File for comparison with id: " + comparisonRequest.getFolderBFiles().getFirst() + " not found!"));
            //TODO test if works for file to file comparisons
            String groupName;
            if (firstFile.getFolder() != null) {
                groupName = firstFile.getFolder().getName();
            } else {
                groupName = firstFile.getOriginalFilename();
            }
            fullPlan.merge(processFolderGroup(comparisonRequest.getFolderBFiles(), comparisonRequest.getGrid(),
                    savedComparison, groupName, savedComparison.getPointFilterLowerBound(),
                    savedComparison.getPointFilterUpperBound(), savedComparison.getNeedOutlierDetection(),
                    savedComparison.getOutlierDeviationFactor(), savedComparison.getNeedPointFilter()));
        }
        if (fileMetadataIds != null && !fileMetadataIds.isEmpty()) {
            fullPlan.merge(processFolderGroup(fileMetadataIds, comparisonRequest.getGrid(), savedComparison,
                    "legacy", savedComparison.getPointFilterLowerBound(), savedComparison.getPointFilterUpperBound(),
                    savedComparison.getNeedOutlierDetection(), savedComparison.getOutlierDeviationFactor(),
                    savedComparison.getNeedPointFilter()));
        }

        //Saving all files (excluded and included)
        List<ComparisonFile> allFiles =
                Stream.concat(
                        fullPlan.getFilesToInclude().stream(),
                        fullPlan.getFilesToExclude().stream()
                ).toList();

        comparisonFileRepository.saveAll(allFiles);

        //Saving folder <-> comparison connections
        if (comparisonRequest.getFolderAId() != null) {
            comparisonFolderRepository.save(new ComparisonFolder(savedComparison.getId(), comparisonRequest.getFolderAId()));
        }
        if (comparisonRequest.getFolderBId() != null) {
            comparisonFolderRepository.save(new ComparisonFolder(savedComparison.getId(), comparisonRequest.getFolderBId()));
        }

        ComparisonDTO dto = mapper.toDto(savedComparison);
        List<Long> allFileIds = allFiles.stream().map(ComparisonFile::getFileId).toList();
        dto.setFiles(metadataService.getMetadataList(allFileIds.stream().map(String::valueOf).toList()));

        eventPublisher.publishEvent(new PreProcessJobsReadyEvent(fullPlan.getJobsToStart()));
        return dto;
    }

    private void validateGrid(CreateComparisonRequest comparisonRequest) throws ValidationException {
        List<String> validationErrors = new ArrayList<>();
        GridParameters gridParameters = comparisonRequest.getGrid();
        double xDist = Math.abs(gridParameters.getxMax() - gridParameters.getxMin());
        if (xDist < gridParameters.getCellWidth()) {
            validationErrors.add("Cell width is bigger than area of interest width");
        }
        double yDist = Math.abs(gridParameters.getyMax() - gridParameters.getyMin());
        if (yDist < gridParameters.getCellHeight()) {
            validationErrors.add("Cell height is bigger than area of interest height");
        }
        //TODO Check if grid AOI inside max / min of total files for FolderA & FolderB / File1 & File2
        if (!validationErrors.isEmpty()) {
            throw new ValidationException("Validation of Grid for comparison: " + comparisonRequest.getName() + " failed!", validationErrors);
        }
    }

    private ComparisonPlan processFolderGroup(List<Long> fileIds, GridParameters grid, Comparison savedComparison,
            String groupName, Double pointFilterLowerBound, Double pointFilterUpperBound,
            Boolean outlierDetectionEnabled, Double outlierDeviationFactor, Boolean needPointFilter)
            throws NotFoundException {
        ComparisonPlan plan = new ComparisonPlan();
        if (fileIds == null || fileIds.isEmpty()) return plan;

        List<File> files = new ArrayList<>();
        for (Long fileId : fileIds) {
            files.add(fileRepository.findById(fileId)
                    .orElseThrow(() -> new NotFoundException("File for comparison with id: " + fileId + " not found!")));
        }
        //TODO: Order by newer dates so newer file is higher prio
        //TODO: Which dates? Upload or capture year?
        List<BoundingBox> restrictedZones = new ArrayList<>();
        for (File fileEntity : files) {
            BoundingBox rawBox = new BoundingBox(fileEntity.getMinX(), fileEntity.getMaxX(), fileEntity.getMinY(), fileEntity.getMaxY());
            BoundingBox snappedBox = snapToGrid(rawBox, grid);

            List<BoundingBox> validRegions = new ArrayList<>();
            validRegions.add(snappedBox);

            for (BoundingBox restrictedZone : restrictedZones) {
                validRegions = subtractRectangleFromList(validRegions, restrictedZone);
                if (validRegions.isEmpty()) break;
            }

            ComparisonFile cf = new ComparisonFile();
            cf.setComparisonId(savedComparison.getId());
            cf.setFileId(fileEntity.getId());
            cf.setGroupName(groupName);
            cf.setStatus(ComparisonFile.Status.PREPROCESSING);

            if (!validRegions.isEmpty()) {
                cf.setIncluded(true);
                String uniqueJobId = UUID.randomUUID().toString();
                StartPreProcessJobDto jobDto = StartPreProcessJobDto.builder()
                        .jobId(uniqueJobId)
                        .grid(grid)
                        .bboxes(validRegions)
                        .comparisonId(savedComparison.getId())
                        .file(new MinioObjectDto("basebucket", fileEntity.getFilename()))
                        .fileId(fileEntity.getId())
                        .outlierDetectionEnabled(outlierDetectionEnabled)
                        .outlierDeviationFactor(outlierDeviationFactor)
                        .pointFilterEnabled(Boolean.TRUE.equals(needPointFilter))
                        .build();

                if(savedComparison.getIndividualStatisticsPercentile() != null) {
                    jobDto.setIndividualPercentile(savedComparison.getIndividualStatisticsPercentile());
                }

                if (pointFilterLowerBound != null) {
                    jobDto.setPointFilterLowerBound(pointFilterLowerBound);
                }
                if (pointFilterUpperBound != null) {
                    jobDto.setPointFilterUpperBound(pointFilterUpperBound);
                }

                plan.addIncludedFile(cf, jobDto);

                restrictedZones.add(snappedBox);

                UUID jobUuid = UUID.fromString(uniqueJobId);

                TrackedJob trackedJob = new TrackedJob(
                        jobUuid,
                        JobType.PREPROCESSING,
                        Map.of("comparisonId", savedComparison.getId(), "fileId", fileEntity.getId()),
                        Instant.now(),
                        Duration.ofMinutes(15)
                );
                jobTrackingService.registerJob(trackedJob);

        } else {
                cf.setIncluded(false);
                cf.setStatus(ComparisonFile.Status.COMPLETED);
                plan.addExcludedFile(cf);
            }
            //TODO: Handle traceability in BE
            //TODO: Check if this is reliable
            //TODO: file1 , file2, file3, ...
        }
        return plan;
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

        //Snap Max (CEIL)
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
        List<BoundingBox> parts = new ArrayList<>();

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
    public ComparisonDTO getComparison(Long comparisonId) throws NotFoundException {
        ComparisonDTO dto = comparisonRepository.findById(comparisonId).map(mapper::toDto).orElse(null);
        if (dto == null) return null;
        reportRepository.findTopByComparisonIdOrderByCreationDateDesc(dto.getId())
                .ifPresent(report -> dto.setLatestReport("/reports/" + report.getId() + "/download"));

        List<Long> fileMetadataIds = comparisonFileRepository.getComparisonFilesByComparisonId(comparisonId);
        List<String> fileMetadataIdStrings = fileMetadataIds.stream()
                .map(String::valueOf)
                .toList();
        setComparisonFolders(comparisonId, dto);

        List<Long> comparisonFolderIds = comparisonFolderRepository.getComparisonFoldersByComparisonId(comparisonId);

        List<FileMetadataDTO> files = metadataService.getMetadataList(fileMetadataIdStrings);
        List<FileMetadataDTO> independentFilesInComparison = files.stream()
                .filter(f -> !comparisonFolderIds.contains(f.getFolderId()))
                .toList();

        dto.setFiles(independentFilesInComparison);

        return dto;
    }

    private void setComparisonFolders(Long comparisonId, ComparisonDTO dto) throws NotFoundException {
        List<Long> cfIds = comparisonFolderRepository.getComparisonFoldersByComparisonId(comparisonId);
        if (cfIds == null || cfIds.isEmpty()) {
            return;
        }

        Folder folderA = findFolderOrThrow(cfIds.get(0));
        dto.setFolderA(new FolderDTO(folderA.getId(), folderA.getName()));

        if (cfIds.size() > 1) {
            Folder folderB = findFolderOrThrow(cfIds.get(1));
            dto.setFolderB(new FolderDTO(folderB.getId(), folderB.getName()));
        }
    }

    private Folder findFolderOrThrow(Long folderId) throws NotFoundException {
        return folderRepository.findById(folderId)
                .orElseThrow(() ->
                        new NotFoundException("Folder for comparison with id: " + folderId + " not found!")
                );
    }

    @Override
    public void startChunkingComparisonJob(Long comparisonId, int chunkingSize) throws NotFoundException {
        ComparisonDTO dto = getComparison(comparisonId);
        if (dto == null) {
            throw new NotFoundException("Comparison with id: " + comparisonId + " not found!");
        }

        // Clear any existing cached result for this comparison and chunk size
        chunkingCacheService.delete(dto.getId(), chunkingSize);


        // TODO Check bucket and object already exist for comparison, else worker throws error and stops
        String bucketName = dto.getResultBucket();
        String objectKey = dto.getResultObjectKey();
        StartChunkingJobDto chunkingJobDto = new StartChunkingJobDto(comparisonId, chunkingSize, new MinioObjectDto(bucketName, objectKey));
        workerStartService.startChunkingComparisonJob(chunkingJobDto);
        log.info("Starting chunking comparison job for comparison with id: {} and chunkSize: {}", comparisonId, chunkingSize);
    }

    @Override
    public void saveVisualizationComparison(Map<String, Object> result) {
        log.info("Received visualization comparison notification");
        if (!result.containsKey("payload")) {
            log.warn("No comparison id or payload found in result of chunking worker");
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        if (!payload.containsKey("comparisonId")) {
            log.warn("No comparison id found in result of chunking worker");
            return;
        }
        Object idObj = payload.get("comparisonId");
        if (!(idObj instanceof Number)) {
            log.warn("Invalid comparisonId in result of chunking worker");
            return;
        }
        Long comparisonId = ((Number) idObj).longValue();

        // Extract chunkSize from payload
        Object chunkSizeObj = payload.get("chunkSize");
        int chunkSize = (chunkSizeObj instanceof Number) ? ((Number) chunkSizeObj).intValue() : 1;

        // Check if result is already cached by worker (new flow)
        Boolean cached = payload.get("cached") instanceof Boolean ? (Boolean) payload.get("cached") : false;

        if (Boolean.TRUE.equals(cached)) {
            // Worker wrote result directly to Redis - just notify SSE subscribers
            log.info("Chunking result for comparison {} (chunkSize={}) already cached by worker, notifying subscribers", comparisonId, chunkSize);
            Optional<Object> cachedResult = chunkingCacheService.get(comparisonId, chunkSize);
            if (cachedResult.isPresent()) {
                eventPublisher.publishEvent(new ChunkingResultReadyEvent(this, comparisonId, chunkSize, cachedResult.get()));
            } else {
                log.warn("Cached flag was true but no result found in Redis for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
            }
            return;
        }

        // Fallback: full payload in message (legacy flow or Redis failure in worker)
        if (!payload.containsKey("chunked_cells")) {
            log.warn("No chunked cells found in result of chunking worker");
            return;
        }

        //TODO: Eventually fix to real dto / model
        Map<String, Object> visualizationResult = new HashMap<>();
        visualizationResult.put("chunked_cells", payload.get("chunked_cells"));

        if (payload.containsKey("statistics")) {
            visualizationResult.put("statistics", payload.get("statistics"));
        }
        if (payload.containsKey("group_mapping")) {
            visualizationResult.put("group_mapping", payload.get("group_mapping"));
        }
        if(payload.containsKey("statistics_p")) {
            visualizationResult.put("statistics_p", payload.get("statistics_p"));
        }
        // Save to Redis cache with chunkSize
        chunkingCacheService.save(comparisonId, chunkSize, visualizationResult);

        // Publish event for SSE subscribers
        eventPublisher.publishEvent(new ChunkingResultReadyEvent(this, comparisonId, chunkSize, visualizationResult));
    }

    /**
     * Returns the result from Redis cache for the specified chunk size.
     */
    public Optional<Object> pollVisualizationResults(Long comparisonId, int chunkSize) {
        return chunkingCacheService.get(comparisonId, chunkSize);
    }


    @Override
    @Transactional
    public void deleteComparisonById(Long id) throws NotFoundException {
        Comparison cp = comparisonRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Comparison with ID " + id + " not found"));

        List<ComparisonFile> cfs = comparisonFileRepository.findAllByComparisonIdAndIncludedTrue(id);

        // 1. Find all files and folders in this comparison and their global usage counts
        List<FileUsageCount> fileUsageCounts = comparisonFileRepository.findFileUsageByComparisonId(id);
        List<FolderUsageCount> folderUsageCounts = comparisonFolderRepository.findFolderUsageByComparisonId(id);

        // 2. Identify files and folders that are ONLY in this comparison (count < 2)
        List<Long> filesToRemove = fileUsageCounts.stream()
                .filter(usage -> usage.getTotalCount() < 2)
                .map(FileUsageCount::getFileId)
                .toList();

        List<Long> foldersToRemove = folderUsageCounts.stream()
                .filter(usage -> usage.getTotalCount() < 2)
                .map(FolderUsageCount::getFolderId)
                .toList();


        // 3. Delete pre-processing results from minio
        for (ComparisonFile cf : cfs) {
            deleteObjectFromMinio(cf.getBucket(), cf.getObjectKey());
        }

        // 4. Delete comparison result
        deleteObjectFromMinio(cp.getResultBucket(), cp.getResultObjectKey());

        // 5. Delete the comparison
        comparisonRepository.deleteById(id);
        log.info("Successfully deleted comparison and related reports for id: {}", id);

        // 6. Clean up the files and folders
        fileRepository.deleteAllById(filesToRemove);
        folderRepository.deleteAllById(foldersToRemove);
    }

    private void deleteObjectFromMinio(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processPreprocessingResult(Map<String, Object> result) {
        log.info("Processing Preprocessing result...");
        log.info("Preprocessing result: {}", result);
        String status = (String) result.get("status");
        String jobId = (String) result.get("job_id");
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");

        if (status == null || jobId == null || payload == null) {
            log.error("Invalid result message received, missing jobId, payload or status");
            return;
        }
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job id received, jobId: " + jobId);
            return;
        }
        jobTrackingService.completeJob(jobUuid);

        Number comparisonIdNum = (Number) payload.get("comparisonId");
        if (comparisonIdNum == null) {
            log.error("Missing comparisonId in preprocessing payload.");
            return;
        }
        Long comparisonId = comparisonIdNum.longValue();

        Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId);
        if (comparisonOpt.isEmpty()) {
            log.error("comparison file entry not found for comparisonId={}", comparisonId);
            return;
        }
        Comparison comparison = comparisonOpt.get();

        Number fileIdNum = (Number) payload.get("fileId");
        if (fileIdNum == null) {
            log.error("Missing fileId in preprocessing payload.");
            persistComparisonErrorPreprocessing(comparison, "Received invalid preprocessing results: missing fileId");
            return;
        }
        Long fileId = fileIdNum.longValue();

        Optional<ComparisonFile> cfOpt = comparisonFileRepository.findComparisonFiles(comparisonId, fileId);
        if (cfOpt.isEmpty()) {
            String errorMsg = String.format(
                    "comparison_file entry not found for comparisonId=%s fileId=%s",
                    comparisonId,
                    fileId
            );
            persistComparisonErrorPreprocessing(comparison, errorMsg);
            return;
        }
        ComparisonFile cf = cfOpt.get();

        if (cf.getStatus().equals(ComparisonFile.Status.FAILED)) {
            return;
        }

        if (!status.equalsIgnoreCase("success")) {
            Object payloadMsg = ((Map<?, ?>) payload).get("msg");
            if (payloadMsg instanceof String errorMessage) {
                log.warn("Preprocessing job {} failed: {}", jobId, errorMessage);
                persistComparisonFileError(cf, errorMessage);
                String comparisonErrorMsg = "Preprocessing failed for one file";
                persistComparisonErrorPreprocessing(comparison, comparisonErrorMsg);
                return;
            }
        }

        Map<String, Object> resultObj = (Map<String, Object>) payload.get("result");
        if (resultObj == null) {
            log.error("Missing result object for file {}", fileId);
            persistComparisonFileError(cf, "Received invalid preprocessing results: missing minio references");
            String comparisonErrorMsg = "Received invalid preprocessing results: missing minio references";
            persistComparisonErrorPreprocessing(comparison, comparisonErrorMsg);
            return;
        }

        String bucket = (String) resultObj.get("bucket");
        String objectKey = (String) resultObj.get("objectKey");
        cf.setBucket(bucket);
        cf.setObjectKey(objectKey);
        cf.setErrorMsg(null);
        cf.setStatus(ComparisonFile.Status.COMPLETED);
        comparisonFileRepository.save(cf);
        checkIfPreprocessingDoneAndStartComparison(comparison, comparisonId, jobId);
    }

    @Override
    public void processComparisonResult(Map<String, Object> result) {
        log.info("Processing Comparison result...");

        String status = (String) result.get("status");
        String jobId = (String) result.get("job_id");
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");

        if (status == null || jobId == null || payload == null) {
            log.error("Invalid result message received, missing jobId, payload or status");
            return;
        }

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job id received, jobId: " + jobId);
            return;
        }
        jobTrackingService.completeJob(jobUuid);

        Object comparisonIdObj = payload.get("comparisonId");
        Long comparisonId;
        if (comparisonIdObj instanceof Number n) {
            comparisonId = n.longValue();
        } else if (comparisonIdObj instanceof String s) {
            comparisonId = Long.parseLong(s);
        } else {
            log.error("Invalid comparisonId type: {}", comparisonIdObj);
            return;
        }

        Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId.longValue());
        if (comparisonOpt.isEmpty()) {
            log.error("comparison  entry not found for comparisonId={}", comparisonId);
            return;
        }
        Comparison comparison = comparisonOpt.get();
        if (comparison.getStatus().equals(Comparison.Status.FAILED)) {
            return;
        }

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

        if (bucket == null || objectKey == null) {
            log.error("Missing bucket or objectKey in payload.");
            persistComparisonError(comparison, "Missing bucket or objectKey in comparison result payload.");
            return;
        }

        comparison.setStatus(Comparison.Status.COMPLETED);
        comparison.setErrorMessage(null);
        comparison.setResultBucket(bucket);
        comparison.setResultObjectKey(objectKey);
        comparisonRepository.save(comparison);
    }

    private void checkIfPreprocessingDoneAndStartComparison(Comparison comparison, Long comparisonId, String jobId) {
        if (comparison.getStatus() == Comparison.Status.FAILED) {
            log.info("Preprocessing of comparison with id {} failed. Comparison worker will not be started.", comparisonId);
            return;
        }

        boolean allReady = comparisonFileRepository.areAllIncludedFilesReady(comparisonId);

        if (allReady) {
            comparison.setStatus(Comparison.Status.COMPARING);
            comparisonRepository.save(comparison);
            List<ComparisonFile> comparisonFiles = comparisonFileRepository.findAllByComparisonIdAndIncludedTrue(comparisonId);
            log.info("Comparison {}: all preprocessing files contain bucket & objectKey. Starting comparison worker...", comparisonId);
            List<ComparisonWorkerInputFileDto> filesDto = comparisonFiles.stream()
                    .map(cf -> new ComparisonWorkerInputFileDto(cf.getBucket(), cf.getObjectKey(), cf.getGroupName()))
                    .toList();

            UUID comparisonJobId = UUID.randomUUID();
            StartComparisonJobDto dto = new StartComparisonJobDto(
                    comparisonJobId.toString(),
                    comparisonId.toString(),
                    filesDto
            );


            TrackedJob trackedJob = new TrackedJob(
                    comparisonJobId,
                    JobType.COMPARISON,
                    Map.of("comparisonId", comparisonId),
                    Instant.now(),
                    Duration.ofMinutes(15)
            );
            jobTrackingService.registerJob(trackedJob);
            workerStartService.startComparisonJob(dto);
        } else {
            log.info("Comparison {} is not ready yet. Waiting for other files.", comparisonId);
        }
    }

    private void persistComparisonError(Comparison comparison, String errorMsg) {
        comparison.setStatus(Comparison.Status.FAILED);
        comparison.setErrorMessage(errorMsg);
        comparisonRepository.save(comparison);
    }

    private void persistComparisonErrorPreprocessing(Comparison comparison, String errorMsg) {
        if (comparison.getStatus() == Comparison.Status.FAILED) {
            comparison.setErrorMessage("Preprocessing of multiple files failed");
        }
        comparison.setStatus(Comparison.Status.FAILED);
        comparison.setErrorMessage(errorMsg);
        comparisonRepository.save(comparison);
    }

    private void persistComparisonFileError(ComparisonFile comparisonFile, String errorMsg) {
        comparisonFile.setStatus(ComparisonFile.Status.FAILED);
        comparisonFile.setErrorMsg(errorMsg);
        comparisonFileRepository.save(comparisonFile);
    }
}
