package com.example.lidarcbackend.api.comparison;

import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.ComparisonRequest;
import com.example.lidarcbackend.api.comparison.dtos.ComparisonResponse;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.service.IImageService;
import com.example.lidarcbackend.service.files.ComparisonService;
import com.example.lidarcbackend.service.reports.IReportService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/v1/comparisons")
@Slf4j
public class ComparisonController {
    private final ComparisonService comparisonService;
    private final IReportService reportService;
    @Value("${app.upload.dir:uploads}")
    private String UPLOAD_DIRECTORY; //TODO: MAYBE CHANGE TO MINIO BUCKET IF WANTED


    @Autowired
    public ComparisonController(ComparisonService comparisonService, IReportService reportService, IImageService imageService) {
        this.comparisonService = comparisonService;
        this.reportService = reportService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<ComparisonDTO>> getAllComparisons() {
        List<ComparisonDTO> comparisons = comparisonService.getAllComparisons();
        return ResponseEntity.ok(comparisons);
    }

    @GetMapping("/paged")
    public ResponseEntity<ComparisonResponse> getPagedMetadata(@Valid @ModelAttribute ComparisonRequest request) {
        Sort sort = request.isAscending() ?
                Sort.by(request.getSortBy()).ascending() :
                Sort.by(request.getSortBy()).descending();

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Page<ComparisonDTO> result = comparisonService.getPagedComparisons(pageable);
        ComparisonResponse response = new ComparisonResponse(
                result.getContent(),
                result.getTotalElements(),
                result.getNumber(),
                request.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComparisonDTO> getComparison(@PathVariable Long id) {
        ComparisonDTO dto = comparisonService.GetComparison(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<ComparisonDTO> saveComparison(
            @RequestBody CreateComparisonRequest request
    ) {
        ComparisonDTO saved = comparisonService.saveComparison(request, request.getFileMetadataIds());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComparison(@PathVariable Long id) {
        comparisonService.deleteComparisonById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/reports")
    public ResponseEntity<?> getReportsOfComparison(@PathVariable Long id) {
        //TODO
        return null;
    }



    @PostMapping(path="/{id}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> createReport(
            @PathVariable Long id,
            @Parameter(description = "Report JSON", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreateReportDto.class)))
            @Valid @RequestPart("report") CreateReportDto report,
            @Parameter(description = "Report images", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestPart(value = "files", required = false) MultipartFile[] files) throws NotFoundException {
        log.info("POST /api/v1/reports");
        try {
            ReportInfoDto createdReport = reportService.createReport(id, report, files);
            Resource resource = new UrlResource(Paths.get(UPLOAD_DIRECTORY).resolve(createdReport.getFileName()).toUri());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + createdReport.getFileName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch(NotFoundException e) {
            logClientError(HttpStatus.NOT_FOUND, "Comparison not found for Report", e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Logs client-side errors with status, message, and exception details.
     *
     * @param status  the HTTP status code
     * @param message a short description of the error
     * @param e       the caught exception
     */
    private void logClientError(HttpStatus status, String message, Exception e) {
        log.warn("{} {}: {}: {}", status.value(), message, e.getClass().getSimpleName(), e.getMessage());
    }
}
