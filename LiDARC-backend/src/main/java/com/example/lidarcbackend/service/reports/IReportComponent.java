package com.example.lidarcbackend.service.reports;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;

import java.io.IOException;

public interface IReportComponent {
    Document render(Document document) throws DocumentException;

    Document render(Document document, byte[] imageId) throws IOException, DocumentException;
}
