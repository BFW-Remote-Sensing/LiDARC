package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.ComparableItemDTO;
import com.example.lidarcbackend.api.metadata.dtos.ComparableProjection;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
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
            log.error("Invalid result message received, missing jobId or status");
            return;
        }

        if (!status.equalsIgnoreCase("success")) {
            Object payload = result.get("payload");
            if (payload instanceof Map) {
                Object payloadMsg = ((Map<?, ?>) payload).get("msg");
                if (payloadMsg instanceof String errorMessage) {
                    log.warn("Metadata job {} failed: {}", jobId, errorMessage);
                    return;
                }
            }
        }

        Object payloadObj = result.get("payload");
        if (!(payloadObj instanceof Map)) {
            log.error("Invalid payload for job {}", jobId);
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        Object metadataObj = payload.get("metadata");
        if (!(metadataObj instanceof Map)) {
            log.error("Invalid metadata object for job {}", jobId);
            return;
        }
        Map<String, Object> metadata = (Map<String, Object>) metadataObj;

        File file = parseMetadata(metadata);
        if (file == null) {
            log.error("Invalid metadata object for job {}", jobId);
        } else {
            fileRepository.save(file);
            log.info("Saved FileMetadata for file: {}", file.getFilename());
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
        short captureYear = castToShort(metadata.get("capture_year"));
        if (captureYear > 1900) {
            file.setCaptureYear(captureYear);
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

        return file;
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
}
