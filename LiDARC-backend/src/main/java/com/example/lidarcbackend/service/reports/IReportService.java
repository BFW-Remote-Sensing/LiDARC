package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;

import java.io.IOException;

public interface IReportService {

    ReportInfoDto createReport(CreateReportDto report) throws IOException;
}
