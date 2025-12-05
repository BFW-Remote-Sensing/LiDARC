package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.model.entity.Report;
import com.example.lidarcbackend.repository.ReportRepository;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ReportService implements IReportService {

    private final ReportComponentFactory reportComponentFactory;
    private final ReportRepository reportRepository;
    private static final String UPLOAD_DIRECTORY = "src/main/resources/static/reports"; //TODO: MAYBE CHANGE TO MINIO BUCKET IF WANTED
    private static final String LOGO_PATH = "src/main/resources/static/images/lidarc_logo.png";
    public ReportService(ReportComponentFactory reportComponentFactory,  ReportRepository reportRepository) {
        this.reportComponentFactory = reportComponentFactory;
        this.reportRepository = reportRepository;
    }
    @Override
    @Transactional
    public ReportInfoDto createReport(CreateReportDto report) throws IOException {
        //TODO: Read config for report aka fetch comparison from table + possible metadata
        String filename = generateUniqueReportName();
        Document document = assembleReport(report.getComponents(), filename);
        Report toCreate = Report.builder().title(report.getTitle()).fileName(filename).build();
        Report created = reportRepository.save(toCreate);
        return ReportInfoDto.builder().id(created.getId()).fileName(created.getFileName()).title(created.getTitle()).build();
    }


    private Document assembleReport(List<ReportComponentDto> components, String uniqueName) throws IOException {
        try {
            Document document = generateBaseLayout(uniqueName);
            for (ReportComponentDto component : components) {
                IReportComponent componentInstance = reportComponentFactory.getReportComponent(component.getType());
                if (component.getFileName() != null && !component.getFileName().isBlank()) {
                    document = componentInstance.render(document, component.getFileName());
                }
                document = componentInstance.render(document);
            }
            document.close();
            return document;
        } catch (DocumentException e) {
            //TODO: What to do here?
            log.warn("Error while creating base layout of report {}", e.getMessage());
        }
        return null;
    }

    private Document generateBaseLayout(String uniqueName) throws IOException, DocumentException {
        Document document = new Document();
        Path uploadPath = Path.of(UPLOAD_DIRECTORY);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(uniqueName);
        PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
        document.open();

        Path imagePath = Path.of(LOGO_PATH);
        Image image = Image.getInstance(imagePath.toAbsolutePath().toString());
        image.scaleToFit(120,60);
        image.setAlignment(Element.ALIGN_CENTER);
        document.add(image);

        Paragraph paragraph = new Paragraph("This is the report!");
        document.add(paragraph);


        return document;
    }

    private String generateUniqueReportName() {
        String uniqueFileName;
        do {
            uniqueFileName = UUID.randomUUID() + ".pdf";
        } while (Files.exists(Path.of(UPLOAD_DIRECTORY, uniqueFileName)));
        return uniqueFileName;
    }
}
