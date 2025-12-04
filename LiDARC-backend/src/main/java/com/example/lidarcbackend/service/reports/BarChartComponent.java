package com.example.lidarcbackend.service.reports;

import com.itextpdf.text.Document;

public class BarChartComponent implements IReportComponent {
    @Override
    public Document render(Document document) {
        return document;
    }

    @Override
    public Document render(Document document, String imageId) {
        return document;
    }
}
