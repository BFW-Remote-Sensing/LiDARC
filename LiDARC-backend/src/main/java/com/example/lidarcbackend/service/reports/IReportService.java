package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;

public interface IReportService {

    ReportInfoDto createReport(CreateReportDto report);
}
