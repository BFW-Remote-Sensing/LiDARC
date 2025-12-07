package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.Report;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.ReportRepository;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.lidarcbackend.api.ReportController.UPLOAD_DIRECTORY;

@Slf4j
@Service
public class ReportService implements IReportService {

    private final ReportComponentFactory reportComponentFactory;
    private final ReportRepository reportRepository;
    private final ComparisonRepository comparisonRepository;
    private static final String LOGO_PATH = "src/main/resources/static/images/lidarc_logo.png";
    public ReportService(ReportComponentFactory reportComponentFactory,  ReportRepository reportRepository, ComparisonRepository comparisonRepository) {
        this.reportComponentFactory = reportComponentFactory;
        this.reportRepository = reportRepository;
        this.comparisonRepository = comparisonRepository;
    }
    @Override
    @Transactional
    public ReportInfoDto createReport(Long id, CreateReportDto report, MultipartFile[] files) throws IOException, NotFoundException {
        //TODO: Read config for report aka fetch comparison from table + possible metadata
        Comparison comparison = comparisonRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Comparison with id " + id + " not found"));
        String filename = generateUniqueReportName();
        Document document = assembleReport(report, filename, files);
        Report toCreate = Report.builder().title(report.getTitle()).fileName(filename).comparison(comparison).build();
        Report created = reportRepository.save(toCreate);
        return ReportInfoDto.builder().id(created.getId()).fileName(created.getFileName()).title(created.getTitle()).build();
    }

    @Override
    public ReportInfoDto getReport(Long reportId) throws NotFoundException {
        Report report = reportRepository.findById(reportId).orElseThrow(
                () -> new NotFoundException("Report with id " + reportId + " not found")
        );
        return ReportInfoDto.builder()
                .id(report.getId())
                .title(report.getTitle())
                .fileName(report.getFileName())
                .build();
    }


    private Document assembleReport(CreateReportDto reportDto, String uniqueName, MultipartFile[] files) throws IOException {
        List<ReportComponentDto> components = reportDto.getComponents();
        Map<String, byte[]> fileToComponent =  new HashMap<>();

        if (files != null) {
            for (MultipartFile file : files) {
                fileToComponent.put(file.getOriginalFilename(), file.getBytes());
            }
        }
        try {
            Document document = generateBaseLayout(uniqueName);
            for (ReportComponentDto component : components) {
                IReportComponent componentInstance = reportComponentFactory.getReportComponent(component.getType());
                if (component.getFileName() != null && !component.getFileName().isBlank()
                    && !fileToComponent.isEmpty() && fileToComponent.containsKey(component.getFileName())) {
                    document = componentInstance.render(document, fileToComponent.get(component.getFileName()));
                } else {
                    document = componentInstance.render(document);
                }
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
