package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.CoordinateSystem;
import com.example.lidarcbackend.model.FileMetadata;
import com.example.lidarcbackend.repository.CoordinateSystemRepository;
import com.example.lidarcbackend.repository.FileMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class MetadataService implements IMetadataService {

    private final FileMetadataRepository fileRepository;
    private CoordinateSystemRepository coordinateSystemRepository;

    public MetadataService(FileMetadataRepository fileRepository, CoordinateSystemRepository coordinateSystemRepository) {
        this.fileRepository = fileRepository;
        this.coordinateSystemRepository = coordinateSystemRepository;
    }


    @Override
    public void processMetadata(Map<String, Object> metadata) {
        log.info("Processing Metadata result...");

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

        FileMetadata fileMetadata = new FileMetadata();
        //text
        fileMetadata.setFilename((String) metadata.get("filename"));
        fileMetadata.setLasVersion((String) metadata.get("las_version"));
        fileMetadata.setCaptureSoftware((String) metadata.get("capture_software"));
        fileMetadata.setSystemIdentifier((String) metadata.get("system_identifier"));

        //numeric
        fileMetadata.setCaptureYear(castToShort(metadata.get("capture_year")));
        fileMetadata.setSizeBytes(castToLong(metadata.get("size_bytes")));
        fileMetadata.setMinX(castToDouble(metadata.get("min_x")));
        fileMetadata.setMinY(castToDouble(metadata.get("min_y")));
        fileMetadata.setMinZ(castToDouble(metadata.get("min_z")));
        fileMetadata.setMaxX(castToDouble(metadata.get("max_x")));
        fileMetadata.setMaxY(castToDouble(metadata.get("max_y")));
        fileMetadata.setMaxZ(castToDouble(metadata.get("max_z")));
        fileMetadata.setPointCount(castToLong(metadata.get("point_count")));

        //date
        fileMetadata.setFileCreationDate(castToLocalDate(metadata.get("file_creation_date")));

        //coordinate system
        if (cs != null) {
            fileMetadata.setCoordinateSystem(cs.getId().intValue());
        } else {
            //TODO should not happen (handle properly)
            fileMetadata.setCoordinateSystem(null);
        }

        // uploaded flags
        fileMetadata.setUploaded(true);
        fileMetadata.setUploadedAt(LocalDateTime.now());

        //TODO handle constraint violations
        fileRepository.save(fileMetadata);
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
