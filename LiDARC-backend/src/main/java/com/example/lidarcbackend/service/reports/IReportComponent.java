package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;

import java.io.IOException;
import java.util.Map;

public interface IReportComponent {

    Document render(Document document, ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException;
}
