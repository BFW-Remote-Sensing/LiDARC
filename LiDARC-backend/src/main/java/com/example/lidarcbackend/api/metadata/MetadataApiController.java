package com.example.lidarcbackend.api.metadata;

import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.api.metadata.dtos.*;
import com.example.lidarcbackend.exception.BadRequestException;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.service.files.CoordinateSystemService;
import com.example.lidarcbackend.service.files.MetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metadata")
@Slf4j
class MetadataApiController {
    private final ObjectMapper objectMapper;

    private final MetadataService metadataService;

    private final CoordinateSystemService coordinateSystemService;

    @Autowired
    public MetadataApiController(
            ObjectMapper objectMapper,
            MetadataService metadataService,
            CoordinateSystemService coordinateSystemService
    ) {
        this.objectMapper = objectMapper;
        this.metadataService = metadataService;
        this.coordinateSystemService = coordinateSystemService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadataDTO> getMetadataById(@PathVariable Long id) {
        if (!metadataService.existsWithId(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadataService.GetMetadata(id.toString()));
    }

    /**
     * List all file metadata without a folder
     *
     * @return list of metadata
     */
    @GetMapping("/unassigned/all")
    public ResponseEntity<List<FileMetadataDTO>> getAllMetadataWithoutFolder() {
        try {
            List<FileMetadataDTO> metadata = metadataService.getAllMetadataWithoutFolder();
            List<FileMetadataDTO> content = metadata.stream()
                    .map(entity -> objectMapper.convertValue(entity, FileMetadataDTO.class))
                    .toList();
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.error("Failed to list metadata", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * List metadata without folder paged
     *
     * @param request MetadataRequest(page, size, sortBy, ascending)
     * @return list of metadata
     */
    //TODO Filter items for only active files
    @GetMapping("/unassigned/paged")
    public ResponseEntity<MetadataResponse> getPagedMetadataWithoutFolder(
            @RequestParam(required = false) String search,
            @Valid @ModelAttribute MetadataRequest request) {
        try {
            Sort sort = request.isAscending() ?
                    Sort.by(request.getSortBy()).ascending() :
                    Sort.by(request.getSortBy()).descending();

            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
            Page<FileMetadataDTO> result = metadataService.getPagedMetadataWithoutFolder(pageable, search);

            MetadataResponse response = new MetadataResponse(
                    result.getContent(),
                    result.getTotalElements(),
                    result.getNumber(),
                    request.getSize()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to list metadata", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/assigned/grouped-by-folder/all")
    public ResponseEntity<List<FolderFilesDTO>> getMetadataGroupedByFolder() {
        try {
            return ResponseEntity.ok(metadataService.getMetadataGroupedByFolder());
        } catch (Exception e) {
            log.error("Failed to list metadata grouped by folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    //TODO Filter items for only active items
    @GetMapping("/all/grouped-by-folder/paged")
    public ResponseEntity<ComparableResponse> getAllMetadataGroupedByFolderPaged(
            @RequestParam(required = false) String search,
            @Valid @ModelAttribute ComparableRequest request
    ) {
        try {
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.unsorted());
            Page<ComparableItemDTO> result = metadataService.getAllMetadataGroupedByFolderPaged(pageable, search);
            return ResponseEntity.ok(
                    new ComparableResponse(
                            result.getContent(),
                            result.getTotalElements()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to list metadata grouped by folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete metadata by ID
     *
     * @param id metadata ID to delete
     * @return empty response with appropriate status
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMetadata(
            @Parameter(
                    description = "Metadata ID to delete",
                    required = true,
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "string")
            )
            @PathVariable Long id
    ) {
        try {
            metadataService.deleteMetadataById(id);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException e) {
            //throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (BadRequestException e) {
            //throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/assign-folder")
    public ResponseEntity<Void> assignFolder(
            @RequestParam Long folderId,
            @RequestBody List<Long> metadataIds
    ) {
        metadataService.assignFolder(metadataIds, folderId);
        return ResponseEntity.noContent().build();
    }
}
