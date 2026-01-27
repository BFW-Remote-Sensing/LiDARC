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

public class SimpleReportComponent implements IReportComponent {

    private static final Map<String, String> CHART_DESCRIPTIONS = Map.of(
        "SCATTER", "The scatter plot visualizes the relationship between vegetation height of item A and item B on a per-grid-cell basis. " +
            "Each point represents a single grid cell, with its X-coordinate indicating the vegetation height from item A and its Y-coordinate " +
            "representing the height from item B. The dashed red line shows the linear regression trend across all points. The Pearson correlation " +
            "coefficient (r) is displayed, quantifying the strength and direction of the linear relationship. ",
        "BOXPLOT", "The boxplot shows the distribution of vegetation heights for each file across all grid cells. Each box represents one comparison item " +
            "and summarizes its minimal vegetation height, the first and third quartile, the median and the maximum vegetation height of the item. " +
            "This visualization provides a compact overview of the spread of the vegetational heights in the two items. ",
        "DISTRIBUTION", "The distribution chart shows a smoothed density estimate of the vegetation heights for item A and item B. The X-axis represents vegetation " +
            "height values, while the Y-axis indicates the probability density. Peaks indicate the most common vegetation heights, while the width of each curve " +
            "represents the spread or variability of the data. ",
        "DISTRIBUTION_DIFF", "The difference distribution chart shows a smoothed density estimate of vegetational height differences between item A and item B. " +
            "The X-axis represents the height difference for each grid cell (B minus A), where negative values indicate that the maximum vegetational height in item B is lower than in A and positive values indicate the maximum vegetational height in B is taller than in A. " +
            "The Y-axis shows the probability density, highlighting how common each difference value is across all grid cells. ",
        "HISTO", "The histogram visualizes the frequency of vegetational height differences across grid cells. " +
            "Each bar represents the number of cells within a specific difference range. " +
            "This allows easy identification of where most grid cells differ, and the magnitude of positive or negative deviations. "
    );

    @Override
    public Document render(Document document, ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException {
        PdfPTable container = createBaseChartTable(componentDto, fileMap);
        String typeKey = String.valueOf(componentDto.getType());
        if (CHART_DESCRIPTIONS.containsKey(typeKey)) {
            addDescriptionRow(container, CHART_DESCRIPTIONS.get(typeKey));
        }
        document.add(container);
        return document;
    }

    protected PdfPTable createBaseChartTable(ReportComponentDto componentDto, Map<String, byte[]> fileMap) throws IOException, DocumentException {
        PdfPTable container = new PdfPTable(1);
        container.setWidthPercentage(100);
        container.setKeepTogether(true);
        container.setSplitLate(false);
        container.setSpacingAfter(20f);

        if (componentDto.getTitle() != null && !componentDto.getTitle().isBlank()) {
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.BLACK);
            PdfPCell titleCell = new PdfPCell(new Paragraph(componentDto.getTitle(), headerFont));
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setPaddingBottom(8f);
            container.addCell(titleCell);
        }
        String fileName = componentDto.getFileName();
        byte[] imageBytes = (fileName != null && fileMap.containsKey(fileName)) ? fileMap.get(fileName) : null;

        PdfPCell contentCell;
        if (imageBytes != null && imageBytes.length > 0) {
            Image image = Image.getInstance(imageBytes);
            image.scaleToFit(520f, 400f);
            contentCell = new PdfPCell(image, true);
            contentCell.setBorder(Rectangle.BOX);
            contentCell.setBorderColor(BaseColor.LIGHT_GRAY);
            contentCell.setBorderWidth(1f);
            contentCell.setPadding(5f);
        } else {
            contentCell = new PdfPCell(new Paragraph("Image not found: " + fileName));
            contentCell.setBorder(Rectangle.NO_BORDER);
        }
        container.addCell(contentCell);

        return container;
    }

    private void addDescriptionRow(PdfPTable container, String text) {
        PdfPCell descCell = new PdfPCell();
        descCell.setBorder(Rectangle.NO_BORDER);
        descCell.setPaddingTop(8f);

        Font descFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.DARK_GRAY);
        Paragraph p = new Paragraph(text, descFont);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(11f);

        descCell.addElement(p);
        container.addCell(descCell);
    }
}
