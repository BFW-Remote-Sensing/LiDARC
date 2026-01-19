package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.ComparableItemDTO;
import com.example.lidarcbackend.api.metadata.dtos.ComparableProjection;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.CoordinateSystem;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.repository.CoordinateSystemRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.service.folders.IFolderService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


@Service
@Slf4j
public class MetadataService implements IMetadataService {

    private final FileRepository fileRepository;
    private final CoordinateSystemRepository coordinateSystemRepository;
    private final FolderRepository folderRepository;
    private final Validator validator;
    private final MetadataMapper mapper;
    private final IFolderService folderService;

    public MetadataService(
            FileRepository fileRepository,
            CoordinateSystemRepository coordinateSystemRepository,
            FolderRepository folderRepository,
            IFolderService folderService,
            Validator validator,
            MetadataMapper mapper) {
        this.fileRepository = fileRepository;
        this.coordinateSystemRepository = coordinateSystemRepository;
        this.folderRepository = folderRepository;
        this.folderService = folderService;
        this.validator = validator;
        this.mapper = mapper;
    }

    public FileMetadataDTO GetMetadata(String metadataId) {
        return fileRepository.findById(Long.parseLong(metadataId)).map(mapper::toDto).orElse(null);
    }

    public List<FileMetadataDTO> getMetadataList(List<String> metadataIds) {

        List<Long> ids = metadataIds.stream()
                .map(Long::parseLong)
                .toList();

        return fileRepository.findAllById(ids).stream()
                .map(mapper::toDto)
                .toList();
    }

    public Boolean existsWithId(Long id) {
        return fileRepository.existsById(id);
    }

    public Page<FileMetadataDTO> getPagedMetadataWithoutFolder(Pageable pageable, String search) {
        Page<File> page;

        if (search == null || search.isBlank()) {
            page = fileRepository.findPagedByFolderIsNull(pageable);
        } else {
            page = fileRepository.findPagedByFolderIsNullAndOriginalFilenameContainingIgnoreCase(
                    search,
                    pageable
            );
        }

        return page.map(mapper::toDto);
    }

    public List<FileMetadataDTO> getAllMetadataWithoutFolder() {
        return fileRepository.findAllByFolderIsNull(Sort.by(Sort.Direction.DESC, "uploadedAt")).stream()
                .map(mapper::toDto)
                .toList();
    }

    public List<FolderFilesDTO> getMetadataGroupedByFolder() {

        List<File> entities =
                fileRepository.findAllByFolderIsNotNull(
                        Sort.by(Sort.Direction.DESC, "uploadedAt")
                );

        Map<Folder, List<File>> grouped =
                entities.stream()
                        .collect(Collectors.groupingBy(File::getFolder));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(
                        entry -> entry.getKey().getCreatedAt(),
                        Comparator.reverseOrder()))
                .map(entry -> new FolderFilesDTO(
                        entry.getKey().getId(),
                        entry.getKey().getName(),
                        entry.getKey().getCreatedAt(),
                        entry.getKey().getStatus(),
                        entry.getValue().stream()
                                .map(mapper::toDto)
                                .toList()
                ))
                .toList();
    }

    public Page<ComparableItemDTO> getAllMetadataGroupedByFolderPaged(Pageable pageable, String search) {
        Page<ComparableProjection> page = fileRepository.findComparables(search != null && !search.isBlank() ? search : null, pageable);

        List<Long> folderIds =
                page.stream()
                        .map(ComparableProjection::getFolderId)
                        .filter(Objects::nonNull)
                        .toList();

        List<Long> fileIds =
                page.stream()
                        .map(ComparableProjection::getFileId)
                        .filter(Objects::nonNull)
                        .toList();

        Map<Long, FolderFilesDTO> folders =
                folderService.loadFoldersWithFiles(folderIds);

        Map<Long, FileMetadataDTO> files =
                this.loadFiles(fileIds);


        return page.map(row -> {
            if (row.getFolderId() != null)
                return folders.get(row.getFolderId());
            return files.get(row.getFileId());
        });
    }

    @Transactional
    @Override
    public void deleteMetadataById(Long id) {
        if (!fileRepository.existsById(id)) {
            throw new RuntimeException("Metadata with id " + id + " not found");
        }
        fileRepository.deleteById(id);
    }

    public Map<Long, FileMetadataDTO> loadFiles(List<Long> fileIds) {

        if (fileIds.isEmpty()) {
            return Map.of();
        }

        return fileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(
                        File::getId,
                        mapper::toDto
                ));
    }

    @Override
    @Transactional
    public void processMetadata(Map<String, Object> result) {
        log.info("Processing Metadata result...");

        String status = (String) result.get("status");
        String jobId = (String) result.get("job_id");

        if (status == null || jobId == null) {
            log.error("Invalid result message received, missing jobId, fileName or status");
            return;
        }

        Object payloadObj = result.get("payload");
        if (!(payloadObj instanceof Map)) {
            log.error("Invalid payload for job {}", jobId);
            return;
        }

        if (!status.equalsIgnoreCase("success")) {
            Object payload = result.get("payload");
            if (payload instanceof Map) {
                Object payloadMsg = ((Map<?, ?>) payload).get("msg");
                Object payloadFileName = ((Map<?, ?>) payload).get("file_name");
                if (payloadMsg instanceof String errorMessage && payloadFileName instanceof String fileName) {
                    log.warn("Metadata job {} failed: {}", jobId, errorMessage);
                    this.persistMetadataError(fileName, errorMessage);
                    return;
                } else {
                    log.error("Invalid error payload for job {}", jobId);
                    return;
                }
            }
        }
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        Object metadataObj = ((Map<?, ?>) payload).get("metadata");
        Object payloadFileName = ((Map<?, ?>) payload).get("file_name");
        if((payloadFileName instanceof String fileName)) {
            if (!(metadataObj instanceof Map)) {
                log.error("Invalid metadata object for job {}", jobId);
                persistMetadataError(fileName,"Received invalid payload from metadata worker");
            } else {
                Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                File file = parseMetadata(metadata);
                if (file == null) {
                    log.error("Invalid metadata object for job {}", jobId);
                    this.persistMetadataError(fileName,"Received invalid payload from metadata worker");
                } else {
                    file.setErrorMsg(null);
                    tryUpdateFolderStatusToProcessed(file);
                    fileRepository.save(file);
                    log.info("Saved FileMetadata for file: {}", file.getFilename());
                }
            }
        } else {
            log.warn("Invalid metadata payload for job {}", jobId);
        }

    }

    @Transactional
    public void assignFolder(List<Long> metadataIds, Long folderId) {
        if (metadataIds == null || metadataIds.isEmpty()) {
            throw new IllegalArgumentException("Metadata ID list cannot be empty");
        }

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Folder not found with id: " + folderId
                ));

        fileRepository.updateFolderForMetadata(metadataIds, folder);
    }

    private File parseMetadata(Map<String, Object> metadata) {
        try {
            Optional<File> old = fileRepository.findFileByFilename(metadata.get("filename").toString());
            if (old.isEmpty()) {
                log.warn("Original file not found in database, skipping save: {}", metadata.get("filename"));
                return null;
            }
            File file = old.get();

            String csString = (String) metadata.get("coordinate_system");
            CoordinateSystem cs = null;
            if (csString != null && !csString.isEmpty()) {
                String[] parts = csString.split(":");
                if (parts.length == 2) {
                    String authority = parts[0].toUpperCase();
                    String code = parts[1];
                    cs = coordinateSystemRepository.findByAuthorityAndCode(authority, code)
                            .orElseGet(() -> {
                                CoordinateSystem newCs = new CoordinateSystem();
                                newCs.setAuthority(authority);
                                newCs.setCode(code);
                                return coordinateSystemRepository.save(newCs);
                            });

                }
            }
            //text
            file.setLasVersion((String) metadata.get("las_version"));
            file.setCaptureSoftware((String) metadata.get("capture_software"));
            file.setSystemIdentifier((String) metadata.get("system_identifier"));

            //numeric
            if(metadata.get("capture_year") != null) {
                short captureYear = castToShort(metadata.get("capture_year"));
                if (captureYear > 1990) {
                    file.setCaptureYear(captureYear);
                }
            }

            file.setSizeBytes(castToLong(metadata.get("size_bytes")));
            file.setMinX(castToDouble(metadata.get("min_x")));
            file.setMinY(castToDouble(metadata.get("min_y")));
            file.setMinZ(castToDouble(metadata.get("min_z")));
            file.setMaxX(castToDouble(metadata.get("max_x")));
            file.setMaxY(castToDouble(metadata.get("max_y")));
            file.setMaxZ(castToDouble(metadata.get("max_z")));
            file.setPointCount(castToLong(metadata.get("point_count")));

            //date
            file.setFileCreationDate(castToLocalDate(metadata.get("file_creation_date")));

            //coordinate system
            if (cs != null) {
                file.setCoordinateSystem(cs);
            } else {
                file.setCoordinateSystem(null);
            }


            Set<ConstraintViolation<File>> violations = validator.validate(file);
            if (!violations.isEmpty()) {
                for (ConstraintViolation<File> v : violations) {
                    log.error("Validation error on {}: {}", v.getPropertyPath(), v.getMessage());
                }
                return null;
            }

            file.setStatus(File.FileStatus.PROCESSED);
            return file;
        } catch (Exception e) {
            log.error("An unexpected error happened during parsing metadata result: {}", e.getMessage());
            return null;
        }

    }

    private Double castToDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Short castToShort(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).shortValue();
        try {
            return Short.parseShort(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long castToLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate castToLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate) return (LocalDate) obj;
        try {
            return LocalDate.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void persistMetadataError(String fileName, String errorMsg) {
        Optional<File> old = fileRepository.findFileByFilename(fileName);
        if (old.isEmpty()) {
            log.warn("Original file not found in database, skipping save: {}", fileName);
            return;
        }
        File file = old.get();
        file.setStatus(File.FileStatus.FAILED);
        file.setErrorMsg(errorMsg);
        fileRepository.save(file);
        tryUpdateFolderStatusToFailed(file);
    }

    private void tryUpdateFolderStatusToProcessed(File file) {
        if (file == null || file.getFolder() == null) return;

        Folder folder = file.getFolder();

        if (folder.getFiles() == null || folder.getFiles().isEmpty()) {
            return;
        }

        if(Objects.equals(folder.getStatus(), "FAILED")) return;

        boolean hasUnfinishedFiles = folder.getFiles().stream()
                .filter(f -> !f.getId().equals(file.getId()))
                .anyMatch(f ->
                        f.getStatus() == File.FileStatus.UPLOADING ||
                                f.getStatus() == File.FileStatus.UPLOADED ||
                                f.getStatus() == File.FileStatus.PROCESSING
                );

        if (!hasUnfinishedFiles) {
            folder.setStatus("PROCESSED");
            folderRepository.save(folder);
        }
    }

    private void tryUpdateFolderStatusToFailed(File file) {
        if (file == null || file.getFolder() == null) return;

        Folder folder = file.getFolder();

        if (folder.getFiles() == null || folder.getFiles().isEmpty()) {
            return;
        }
        folder.setStatus("FAILED");
        folderRepository.save(folder);
    }




}
