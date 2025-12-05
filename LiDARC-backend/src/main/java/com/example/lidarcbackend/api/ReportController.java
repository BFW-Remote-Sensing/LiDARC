package com.example.lidarcbackend.api;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> createReport(
        @Parameter(description = "Report JSON", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CreateReportDto.class)))
        @Valid @RequestPart("report") CreateReportDto report,
        @Parameter(description = "Report images", required = false, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
        @RequestPart(value = "files", required = false) MultipartFile[] files) {
        log.info("POST /api/v1/reports");
        try {
            ReportInfoDto createdReport = reportService.createReport(report, files);
            Resource resource = new UrlResource(Paths.get(UPLOAD_DIRECTORY).resolve(createdReport.getFileName()).toUri());
            return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + createdReport.getFileName() + "\"")
                .body(resource);
        } catch (IOException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
}
