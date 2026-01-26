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
        dto.setStatus(entity.getStatus().toString());
        dto.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);
        dto.setErrorMessage(entity.getErrorMsg());
        dto.setActive(entity.getActive());

        if (entity.getCoordinateSystem() != null) {
            String authority = entity.getCoordinateSystem().getAuthority();
            String code = entity.getCoordinateSystem().getCode();
            if (authority != null && code != null) {
                dto.setCoordinateSystem(authority + ":" + code);
            }
        }

        return dto;
    }
}
