package com.example.lidarcbackend.service.reports;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;

import java.io.IOException;

public class SimpleReportComponent implements IReportComponent {
    @Override
    public Document render(Document document) throws DocumentException {
        Paragraph paragraph = new Paragraph("Simple Report Component!");
        document.add(paragraph);
        return document;
    }

    @Override
    public Document render(Document document, byte[] imageBytes) throws IOException, DocumentException {
        Image image = Image.getInstance(imageBytes);
        image.scaleToFit(250,250);
        image.setAlignment(Element.ALIGN_CENTER);
        document.add(image);
        return document;
    }
}
