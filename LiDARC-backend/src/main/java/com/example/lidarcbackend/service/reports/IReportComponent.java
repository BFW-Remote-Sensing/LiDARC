package com.example.lidarcbackend.service.reports;

import com.itextpdf.text.Document;

public interface IReportComponent {
    Document render(Document document);

    Document render(Document document, String imageId);
}
