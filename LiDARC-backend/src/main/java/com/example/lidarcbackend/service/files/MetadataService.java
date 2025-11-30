package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.entity.CoordinateSystem;
import com.example.lidarcbackend.model.FileMetadata;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.repository.CoordinateSystemRepository;
import com.example.lidarcbackend.repository.FileMetadataRepository;
import com.example.lidarcbackend.repository.FileRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class MetadataService implements IMetadataService {

    private final FileRepository fileRepository;
    private final CoordinateSystemRepository coordinateSystemRepository;
    private final Validator validator;

    public MetadataService(FileRepository fileRepository, CoordinateSystemRepository coordinateSystemRepository, Validator validator) {
        this.fileRepository = fileRepository;
        this.coordinateSystemRepository = coordinateSystemRepository;
        this.validator = validator;
    }


    @Override
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

    private File parseMetadata(Map<String, Object> metadata) {
        String csString = (String) metadata.get("coordinate_system");
        CoordinateSystem cs = null;
        if(csString != null && !csString.isEmpty()) {
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

        File file = new File();
        //text
        file.setFilename((String) metadata.get("filename"));
        file.setLasVersion((String) metadata.get("las_version"));
        file.setCaptureSoftware((String) metadata.get("capture_software"));
        file.setSystemIdentifier((String) metadata.get("system_identifier"));

        //numeric
        file.setCaptureYear(castToShort(metadata.get("capture_year")));
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
        if(!violations.isEmpty()) {
            for (ConstraintViolation<File> v : violations) {
                log.error("Validation error on {}: {}", v.getPropertyPath(), v.getMessage());
            }
            return null;
        }

        if (fileRepository.findFileByOriginalFilename(file.getFilename()).isPresent()) {
            log.warn("Duplicate original filename detected, skipping save: {}", file.getFilename());
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
