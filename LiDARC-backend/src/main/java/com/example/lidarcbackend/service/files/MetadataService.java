package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.FileMetadata;
import com.example.lidarcbackend.repository.FileMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class MetadataService implements IMetadataService {

    private FileMetadataRepository repository;

    public MetadataService(FileMetadataRepository repository) {
        this.repository = repository;
    }

    @Override
    public void processMetadata(Map<String, Object> metadata) {
        log.info("Processing Metadata result:");
        FileMetadata file = new FileMetadata();
        // Basic string fields
        file.setFilename((String) metadata.get("filename"));
        file.setLasVersion((String) metadata.get("las_version"));
        file.setCaptureSoftware((String) metadata.get("capture_software"));

        // Numeric fields with conversions
        Object creationYearObj = metadata.get("creation_year");
        if (creationYearObj != null) {
            file.setCreationYear(((Number) creationYearObj).shortValue());
        }

        Object sizeObj = metadata.get("size_bytes");
        if (sizeObj != null) {
            file.setSizeBytes(((Number) sizeObj).longValue());
        }

        file.setMinX(castToDouble(metadata.get("min_x")));
        file.setMinY(castToDouble(metadata.get("min_y")));
        file.setMinZ(castToDouble(metadata.get("min_z")));
        file.setMinGpsTime(castToDouble(metadata.get("min_gpstime")));

        file.setMaxX(castToDouble(metadata.get("max_x")));
        file.setMaxY(castToDouble(metadata.get("max_y")));
        file.setMaxZ(castToDouble(metadata.get("max_z")));
        file.setMaxGpsTime(castToDouble(metadata.get("max_gpstime")));

        // Coordinate system parsing (e.g., "31256;austria2022")
        String coordRaw = (String) metadata.get("coordinate_system");
        if (coordRaw != null) {
            try {
                file.setCoordinateSystem(Integer.parseInt(coordRaw.split(";")[0]));
            } catch (NumberFormatException e) {
                // fallback or log error
                file.setCoordinateSystem(null);
            }
        }

        // Uploaded flags
        file.setUploaded(true);
        file.setUploadedAt(LocalDateTime.now());

        // Save to database
        repository.save(file);

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
}
