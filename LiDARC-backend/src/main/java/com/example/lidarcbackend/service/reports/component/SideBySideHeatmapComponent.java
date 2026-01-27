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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class SideBySideHeatmapComponent implements IReportComponent {

    @Override
    public Document render(Document document, ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException {
        String rawFileName = componentDto.getFileName();
        if (rawFileName == null || !rawFileName.contains(";")) {
            return document;
        }
        String[] fileNames = rawFileName.split(";");
        byte[] leftBytes = fileMap.get(fileNames[0]);
        byte[] rightBytes = fileMap.get(fileNames[1]);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1, 1});
        table.setSpacingBefore(10f);
        table.setKeepTogether(true);

        if (componentDto.getTitle() != null) {
            PdfPCell headerCell = new PdfPCell(new Paragraph(componentDto.getTitle(), new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)));
            headerCell.setColspan(2);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPaddingBottom(5f);
            table.addCell(headerCell);
        }

        //TODO: Either add real caption or remove it?
        table.addCell(createImageCell(leftBytes, ""));
        table.addCell(createImageCell(rightBytes, ""));

        document.add(table);
        return document;
    }

    private PdfPCell createImageCell(byte[] bytes, String caption) throws IOException, DocumentException {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5f);

        if (bytes != null) {
            Image img = Image.getInstance(bytes);
            img.scaleToFit(250, 250);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(1f);
            img.setBorderColor(BaseColor.LIGHT_GRAY);

            cell.addElement(img);

            Paragraph p = new Paragraph(caption, new Font(Font.FontFamily.HELVETICA, 9, Font.ITALIC));
            p.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p);
        }
        return cell;
    }

    private void addHeader(Document document, String title) throws DocumentException {
        if (title == null) {
            return;
        }
        Paragraph p = new Paragraph(title, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
        p.setSpacingAfter(5f);
        document.add(p);
    }
}
