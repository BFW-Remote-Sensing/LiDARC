package com.example.lidarcbackend.api;

import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ImageInfoDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.service.IImageService;
import com.example.lidarcbackend.service.reports.IReportService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/api/v1/reports")
@Slf4j
public class ReportController {
    private final IReportService reportService;
    private final IImageService imageService;
    public static final String UPLOAD_DIRECTORY = "src/main/resources/static/reports"; //TODO: MAYBE CHANGE TO MINIO BUCKET IF WANTED

    @Autowired
    public ReportController(IReportService reportService, IImageService imageService) {
        this.reportService = reportService;
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<?> getReports() {
        //TODO
        return null;
    }

    @PostMapping("/images")
    public ResponseEntity<List<ImageInfoDto>> uploadImages(@RequestPart(value = "files", required = false) MultipartFile[] files) {
        log.info("POST /api/v1/reports/images");
        List<ImageInfoDto> images = new ArrayList<>();
        Arrays.asList(files).forEach(f -> {
            try {
                images.add(imageService.save(f.getInputStream(), f.getSize(), f.getOriginalFilename()));
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(images);
    }

    @GetMapping("/{reportId}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long reportId) {
        log.info("GET /api/v1/reports/" + reportId + "/download");
        try {
            ReportInfoDto createdReport = reportService.getReport(reportId);
            Resource resource = new UrlResource(Paths.get(UPLOAD_DIRECTORY).resolve(createdReport.getFileName()).toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + createdReport.getFileName() + "\"")
                        .body(resource);
            }
            log.warn("Report Resource does not exist or is not readable");
            return ResponseEntity.notFound().build();
        } catch (MalformedURLException e) {
            log.warn(e.getMessage());
            //TODO: Change
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (NotFoundException e) {
            logClientError(HttpStatus.NOT_FOUND, "Report not found", e);
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
