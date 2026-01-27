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
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;

import java.io.IOException;
import java.util.Map;

public class HeatmapReportComponent implements IReportComponent {

    @Override
    public Document render(Document document, ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException {
        String fileName = componentDto.getFileName();
        if (fileName != null && fileMap.containsKey(fileName)) {
            byte[] bytes = fileMap.get(fileName);

            PdfPTable container = new PdfPTable(1);
            container.setWidthPercentage(100);
            container.setKeepTogether(true);
            container.setSpacingBefore(10f);

            PdfPCell headerCell = new PdfPCell();
            headerCell.setBorder(Rectangle.NO_BORDER);
            Font subHeaderFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Paragraph p = new Paragraph("Heatmap Analysis", subHeaderFont);
            p.setSpacingAfter(10f);
            headerCell.addElement(p);
            container.addCell(headerCell);

            Image image = Image.getInstance(bytes);
            image.scaleToFit(520, 520);
            image.setBorder(Rectangle.BOX);
            image.setBorderWidth(1f);
            image.setBorderColor(BaseColor.LIGHT_GRAY);

            PdfPCell imgCell = new PdfPCell(image);
            imgCell.setBorder(Rectangle.NO_BORDER);
            imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            container.addCell(imgCell);
            document.add(container);
        }
        return document;
    }

    private void addHeader(Document document) throws DocumentException {
        Font subHeaderFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        Paragraph p = new Paragraph("Heatmap Analysis", subHeaderFont);
        p.setSpacingBefore(10f);
        p.setSpacingAfter(10f);
        document.add(p);
    }
}
