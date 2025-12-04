package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.itextpdf.text.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportService implements IReportService {

    ReportComponentFactory reportComponentFactory;
    public ReportService(ReportComponentFactory reportComponentFactory) {
        this.reportComponentFactory = reportComponentFactory;
    }
    @Override
    public ReportInfoDto createReport(CreateReportDto report) {
        //TODO: Read config for report aka fetch comparison from table + possible metadata
        Document document = assembleReport(report.getComponents());
        return new ReportInfoDto();
    }


    private Document assembleReport(List<ReportComponentDto> components) {
        Document document = generateBaseLayout();

        for (ReportComponentDto component : components) {
            IReportComponent componentInstance = reportComponentFactory.getReportComponent(component.getType());
            if (component.getImageId() != null && !component.getImageId().isBlank()) {
                document = componentInstance.render(document, component.getImageId());
            }
            document = componentInstance.render(document);
        }

        return document;
    }

    private Document generateBaseLayout() {
        return null;
    }
}
