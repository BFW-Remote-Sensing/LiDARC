package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface IReportService {

    ReportInfoDto createReport(Long id, CreateReportDto report, MultipartFile[] files) throws IOException, NotFoundException;

    ReportInfoDto getReport(Long reportId) throws NotFoundException;

    List<ReportInfoDto> getReportsOfComparsion(Long comparisonId, Integer limit) throws NotFoundException;

    Page<ReportInfoDto> getAllReports(Pageable pageable, String search);

    void deleteReport(Long reportId) throws NotFoundException;
}
