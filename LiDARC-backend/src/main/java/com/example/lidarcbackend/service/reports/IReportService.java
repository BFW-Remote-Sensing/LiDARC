package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IReportService {

    ReportInfoDto createReport(CreateReportDto report, MultipartFile[] files) throws IOException;
}
