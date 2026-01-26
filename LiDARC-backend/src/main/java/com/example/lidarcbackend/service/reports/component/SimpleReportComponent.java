package com.example.lidarcbackend.service.reports.component;

import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.example.lidarcbackend.service.reports.IReportComponent;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;

import java.io.IOException;
import java.util.Map;

public class SimpleReportComponent implements IReportComponent {

    @Override
    public Document render(Document document, ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException {
        if (componentDto.getTitle() != null && !componentDto.getTitle().isBlank()) {
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.BLACK);
            Paragraph p = new Paragraph(componentDto.getTitle(), headerFont);
            p.setSpacingAfter(5f);
            document.add(p);
        }
        String fileName = componentDto.getFileName();
        byte[] imageBytes = null;

        if (fileName != null && fileMap.containsKey(fileName)) {
            imageBytes = fileMap.get(fileName);
        }
        if (imageBytes != null && imageBytes.length > 0) {
            Image image = Image.getInstance(imageBytes);
            image.scaleToFit(500f, 500f);
            image.setAlignment(Element.ALIGN_CENTER);
            image.setBorder(Rectangle.BOX);
            image.setBorderWidth(1f);
            image.setBorderColor(BaseColor.LIGHT_GRAY);
            document.add(image);
        } else {
            Paragraph p = new Paragraph("[Image not found: " + fileName + "]", new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC, BaseColor.RED));
            document.add(p);
        }
        return document;
    }
}
