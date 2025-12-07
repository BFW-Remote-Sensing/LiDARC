package com.example.lidarcbackend.api.metadata;

import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.api.metadata.dtos.MetadataRequest;
import com.example.lidarcbackend.api.metadata.dtos.MetadataResponse;
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
     * List metadata paged
     *
     * @return list of all metadata
     */
    @GetMapping("/all")
    public ResponseEntity<List<FileMetadataDTO>> getAllMetadata() {
        try {
            List<FileMetadataDTO> all = metadataService.getAllMetadata();
            List<FileMetadataDTO> content = all.stream()
                    .map(entity -> objectMapper.convertValue(entity, FileMetadataDTO.class))
                    .toList();
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.error("Failed to list metadata", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * List metadata paged
     *
     * @param request MetadataRequest(page, size, sortBy, ascending)
     * @return list of all metadata
     */
    @GetMapping("/paged")
    public ResponseEntity<MetadataResponse> getPagedMetadata(@Valid @ModelAttribute MetadataRequest request) {
        try {
            Sort sort = request.isAscending() ?
                    Sort.by(request.getSortBy()).ascending() :
                    Sort.by(request.getSortBy()).descending();

            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
            Page<FileMetadataDTO> result = metadataService.getPagedMetadata(pageable);

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

    /**
     *
     * @param metadata metadata object to store
     * @return newly created metadata object
     */
//    @PostMapping
//    public ResponseEntity<FileMetadataDTO> createMetadata(
//            @Valid @RequestBody CreateFileMetadataRequest metadata) {
//        try {
//            boolean coordSystemExists = coordinateSystemService.existsWithId(Long.valueOf(metadata.getCoordinateSystem()));
//            if (!coordSystemExists) {
//                return new ResponseEntity<FileMetadataDTO>(HttpStatus.NOT_FOUND);
//            }
//
//            File entity = objectMapper.convertValue(metadata, File.class);
//
//            File savedEntity = metadataService.saveMetadata(entity);
//
//            File savedDTO = objectMapper.convertValue(savedEntity, FileMetadataDTO.class);
//
//            return ResponseEntity
//                    .status(HttpStatus.CREATED)
//                    .body(savedDTO);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).build();
//        }
//    }

    /**
     * Delete metadata by ID
     *
     * @param id metadata ID to delete
     * @return empty response with appropriate status
     */
    @DeleteMapping("/metadata/{id}")
    public ResponseEntity<Void> deleteMetadata(
            @Parameter(
                    description = "Metadata ID to delete",
                    required = true,
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "string")
            )
            @PathVariable Long id
    ) {
        metadataService.deleteMetadataById(id);
        return ResponseEntity.noContent().build();
    }

}
