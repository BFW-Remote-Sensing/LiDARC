package com.example.lidarcbackend.api.metadata;

import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.model.entity.File;
import org.springframework.stereotype.Component;

@Component
public class MetadataMapper {

    public FileMetadataDTO toDto(File entity) {
        if (entity == null) {
            return null;
        }

        FileMetadataDTO dto = new FileMetadataDTO();

        dto.setId(entity.getId());
        dto.setFilename(entity.getFilename());
        dto.setOriginalFilename(entity.getOriginalFilename());
        dto.setCaptureYear(entity.getCaptureYear());
        dto.setSizeBytes(entity.getSizeBytes());

        dto.setMinX(entity.getMinX());
        dto.setMinY(entity.getMinY());
        dto.setMinZ(entity.getMinZ());

        dto.setMaxX(entity.getMaxX());
        dto.setMaxY(entity.getMaxY());
        dto.setMaxZ(entity.getMaxZ());

        dto.setSystemIdentifier(entity.getSystemIdentifier());
        dto.setLasVersion(entity.getLasVersion());
        dto.setCaptureSoftware(entity.getCaptureSoftware());
        dto.setUploaded(entity.getUploaded());
        dto.setFileCreationDate(entity.getFileCreationDate());
        dto.setPointCount(entity.getPointCount());
        dto.setUploadedAt(entity.getUploadedAt());
        dto.setStatus(entity.getStatus());
        dto.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);

        return dto;
    }
}
