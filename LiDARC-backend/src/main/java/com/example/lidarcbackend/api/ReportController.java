package com.example.lidarcbackend.api;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ImageInfoDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.service.IImageService;
import com.example.lidarcbackend.service.reports.IReportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Slf4j
public class ReportController {
    private final IReportService reportService;
    private final IImageService imageService;
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

    @PostMapping
    public ResponseEntity<ReportInfoDto> createReport(@Valid @RequestBody CreateReportDto report) {
        log.info("POST /api/v1/reports");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(report));
        } catch (IOException e){
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
        return  ResponseEntity.status(HttpStatus.CREATED).body(images);
    }
}
