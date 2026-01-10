package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.api.metadata.dtos.ComparableItemDTO;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;


public interface IMetadataService {
    Page<FileMetadataDTO> getPagedMetadataWithoutFolder(Pageable pageable, String search);

    List<FileMetadataDTO> getAllMetadataWithoutFolder();

    List<FolderFilesDTO> getMetadataGroupedByFolder();

    Page<ComparableItemDTO> getAllMetadataGroupedByFolderPaged(Pageable pageable, String search);

    FileMetadataDTO GetMetadata(String metadataId);

    List<FileMetadataDTO> getMetadataList(List<String> metadataIds);

    @Transactional
    void deleteMetadataById(Long id);

    /**
     * Processes a metadata worker result message
     *
     * @param result the result message from the worker; can either be success message or error message
     */
    void processMetadata(Map<String, Object> result);
}
