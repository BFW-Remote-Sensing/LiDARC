package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.CreateReportDto;
import com.example.lidarcbackend.model.DTO.ReportComponentDto;
import com.example.lidarcbackend.model.DTO.ReportInfoDto;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.model.entity.Report;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonFolderRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.repository.ReportRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


@Slf4j
@Service
public class ReportService implements IReportService {

    private final ReportComponentFactory reportComponentFactory;
    private final ReportRepository reportRepository;
    private final ComparisonRepository comparisonRepository;
    private final ComparisonFileRepository comparisonFileRepository;
    private final ComparisonFolderRepository comparisonFolderRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private static final String LOGO_PATH = "src/main/resources/static/images/lidarc_logo.png";
    @Value("${app.upload.dir:/app/uploads}")
    private String UPLOAD_DIRECTORY;

    public ReportService(ReportComponentFactory reportComponentFactory, ReportRepository reportRepository, ComparisonRepository comparisonRepository, ComparisonFileRepository comparisonFileRepository,
                         ComparisonFolderRepository comparisonFolderRepository, FileRepository fileRepository, FolderRepository folderRepository) {
        this.reportComponentFactory = reportComponentFactory;
        this.reportRepository = reportRepository;
        this.comparisonRepository = comparisonRepository;
        this.comparisonFileRepository = comparisonFileRepository;
        this.comparisonFolderRepository = comparisonFolderRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
    }

    @Override
    @Transactional
    public ReportInfoDto createReport(Long id, CreateReportDto report, MultipartFile[] files) throws NotFoundException {
        //TODO: Read config for report aka fetch comparison from table + possible metadata
        Comparison comparison = comparisonRepository.findById(id).orElseThrow(
            () -> new NotFoundException("Comparison with id " + id + " not found"));
        String filename = generateUniqueReportName();
        try {
            Document document = createDocument(filename);
            Document baseLayout = generateBaseLayout(document, report.getTitle(), comparison);
            document = assembleReport(report, files, baseLayout);
        } catch (DocumentException | IOException e) {
            log.error("Error while creating report {}", e.getMessage());
        }
        Report toCreate = Report.builder().title(report.getTitle()).fileName(filename).comparison(comparison).build();
        Report created = reportRepository.save(toCreate);
        return ReportInfoDto.builder().id(created.getId()).fileName(created.getFileName()).title(created.getTitle()).build();
    }

    private Document createDocument(String uniqueName) throws IOException, DocumentException {
        Document document = new Document();
        Path uploadPath = Path.of(UPLOAD_DIRECTORY);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(uniqueName);
        PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
        document.open();
        return document;
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

    @Override
    public List<ReportInfoDto> getReportsOfComparsion(Long comparisonId, Integer limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("creationDate").descending());
        List<Report> reports = this.reportRepository.findByComparisonId(comparisonId, pageable);
        if (reports.isEmpty()) {
            return Collections.emptyList();
        }
        return reports.stream()
            .map(report -> ReportInfoDto.builder()
                .id(report.getId())
                .title(report.getTitle())
                .fileName(report.getFileName())
                .creationDate(report.getCreationDate())
                .build())
            .toList();
    }

    @Override
    public Page<ReportInfoDto> getAllReports(Pageable pageable, String search) {
        log.trace("getAllReports({}, {})", pageable, search);
        Page<Report> reports;
        if (search != null && !search.trim().isEmpty()) {
            reports = reportRepository.findByTitleContainingIgnoreCaseOrFileNameContainingIgnoreCase(search, search, pageable);
        } else {
            reports = reportRepository.findAll(pageable);
        }
        return reports.map(report -> ReportInfoDto.builder()
            .id(report.getId())
            .title(report.getTitle())
            .fileName(report.getFileName())
            .creationDate(report.getCreationDate())
            .comparisonId(report.getComparison() != null ? report.getComparison().getId() : null) //Currently like this for potential that in the future comparison might be null?
            .build());
    }


    @Transactional
    public void deleteReport(Long reportId) throws NotFoundException {
        if (!reportRepository.existsById(reportId)) {
            throw new NotFoundException("Report with id " + reportId + " not found");
        }
        reportRepository.deleteById(reportId);
    }

    private Document assembleReport(CreateReportDto reportDto, MultipartFile[] files, Document document) throws IOException {
        //TODO: Delete / make debug
        log.info("Assembling report with: {}", reportDto);
        List<ReportComponentDto> components = reportDto.getComponents();
        Map<String, byte[]> fileToComponent = new HashMap<>();

        if (files != null) {
            for (MultipartFile file : files) {
                fileToComponent.put(file.getOriginalFilename(), file.getBytes());
            }
        }
        try {
            if (components != null && !components.isEmpty()) {
                document.newPage();
                Font chapterFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.DARK_GRAY);
                Paragraph chapterTitle = new Paragraph("Visualizations", chapterFont);
                chapterTitle.setSpacingBefore(15f);
                chapterTitle.setSpacingAfter(15f);

                document.add(chapterTitle);
            }
            for (ReportComponentDto component : components) {
                IReportComponent componentInstance = reportComponentFactory.getReportComponent(String.valueOf(component.getType()));
                if (componentInstance == null) {
                    log.warn("Called Report generation with report type that does not exist: {}", component.getType());
                    continue;
                }
                if (component.getFileName() != null && !component.getFileName().isBlank()
                    && !fileToComponent.isEmpty() && fileToComponent.containsKey(component.getFileName())) {
                    document = componentInstance.render(document, fileToComponent.get(component.getFileName()));
                } else {
                    document = componentInstance.render(document);
                }
                document.add(new Paragraph("\n"));
            }
            document.close();
            return document;
        } catch (DocumentException e) {
            //TODO: What to do here?
            log.error("Error while assembling report {}", e.getMessage());
            if (document.isOpen()) {
                document.close();
            }
        }
        return null;
    }

    private Document generateBaseLayout(Document document, String reportTitle, Comparison comparison) throws IOException, DocumentException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[] {2f, 6f, 2f});

        Path imagePath = Path.of(LOGO_PATH);
        if (Files.exists(imagePath)) {
            Image image = Image.getInstance(imagePath.toAbsolutePath().toString());
            image.scaleToFit(80, 80);
            PdfPCell logoCell = new PdfPCell(image);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            headerTable.addCell(logoCell);
        } else {
            PdfPCell empty = new PdfPCell();
            empty.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(empty);
        }

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);

        Paragraph titlePara = new Paragraph(reportTitle, titleFont);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titleCell.addElement(titlePara);
        headerTable.addCell(titleCell);

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Font dateFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.GRAY);
        Paragraph datePara = new Paragraph(dateStr, dateFont);
        datePara.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(datePara);
        headerTable.addCell(dateCell);

        PdfPCell spacerCell = new PdfPCell();
        spacerCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(spacerCell);
        document.add(headerTable);

        LineSeparator ls = new LineSeparator();
        ls.setLineColor(BaseColor.LIGHT_GRAY);
        document.add(new Paragraph("\n"));
        document.add(ls);
        document.add(new Paragraph("\n"));

        if (comparison != null) {
            addComparisonMetadata(document, comparison);
            document.add(new Paragraph("\n"));
        }

        document.add(ls);
        return document;

    }

    private void addComparisonMetadata(Document document, Comparison comparison) throws DocumentException {
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[] {1, 3});
        metaTable.setSpacingBefore(10f);

        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.DARK_GRAY);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK);
        Font detailLabelFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.DARK_GRAY);
        Font detailValueFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.BLACK);

        addMetaRow(metaTable, "Comparison Name:", comparison.getName(), labelFont, valueFont);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dateStr = comparison.getCreatedAt() != null ? comparison.getCreatedAt().format(dtf) : "N/A";
        addMetaRow(metaTable, "Created At:", dateStr, labelFont, valueFont);

        //TODO: Check if we want to keep that?
        addMetaRow(metaTable, "Status:", String.valueOf(comparison.getStatus()), labelFont, valueFont);

        addComparisonSources(metaTable, comparison, labelFont, detailLabelFont, detailValueFont);

        String gridInfo = String.format("Width: %sm, Height: %sm (X: %s to %s, Y: %s to %s)",
            comparison.getGridCellWidth(), comparison.getGridCellHeight(),
            comparison.getGridMinX(), comparison.getGridMaxX(),
            comparison.getGridMinY(), comparison.getGridMaxY());
        addMetaRow(metaTable, "Grid Configuration:", gridInfo, labelFont, valueFont);

        List<String> features = new ArrayList<>();
        if (Boolean.TRUE.equals(comparison.getNeedOutlierDetection())) {
            features.add("Outlier Detection");
        }
        if (Boolean.TRUE.equals(comparison.getNeedPointFilter())) {
            features.add("Point Filter (" + comparison.getPointFilterLowerBound() + " - " + comparison.getPointFilterUpperBound() + ")");
        }

        String featureStr = features.isEmpty() ? "None" : String.join(", ", features);
        addMetaRow(metaTable, "Enabled Features:", featureStr, labelFont, valueFont);

        if (comparison.getErrorMessage() != null && !comparison.getErrorMessage().isBlank()) {
            Font errorFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.RED);
            addMetaRow(metaTable, "Error Message:", comparison.getErrorMessage(), labelFont, errorFont);
        }

        document.add(metaTable);

    }

    private void addComparisonSources(PdfPTable table, Comparison comparison, Font mainLabelFont, Font detailBold, Font detailNormal) {
        List<Long> fileIds = this.comparisonFileRepository.getComparisonFilesByComparisonId(comparison.getId());
        List<Long> folderIds = this.comparisonFolderRepository.getComparisonFoldersByComparisonId(comparison.getId());

        List<File> comparisonFiles = new ArrayList<>();
        for (Long fileId : fileIds) {
            this.fileRepository.findById(fileId).ifPresent(comparisonFiles::add);
        }
        List<Folder> comparisonFolders = new ArrayList<>();
        for (Long folderId : folderIds) {
            this.folderRepository.findById(folderId).ifPresent(comparisonFolders::add);
        }

        if (comparisonFolders.size() == 2) {
            addRichRow(table, "Reference Source:", createFolderDetailsCell(comparisonFolders.getFirst(), detailBold, detailNormal), mainLabelFont);
            addRichRow(table, "Target Source:", createFolderDetailsCell(comparisonFolders.getLast(), detailBold, detailNormal), mainLabelFont);
            return;
        }
        if (comparisonFiles.size() == 2 && comparisonFolders.isEmpty()) {
            addRichRow(table, "Reference Source:", createFileDetailsCell(comparisonFiles.getFirst(), detailBold, detailNormal), mainLabelFont);
            addRichRow(table, "Target Source:", createFileDetailsCell(comparisonFiles.getLast(), detailBold, detailNormal), mainLabelFont);
            return;
        }
        if (comparisonFolders.size() == 1 && comparisonFiles.size() == 1) {
            addRichRow(table, "Reference Source:", createFolderDetailsCell(comparisonFolders.getFirst(), detailBold, detailNormal), mainLabelFont);
            addRichRow(table, "Target Source:", createFileDetailsCell(comparisonFiles.getFirst(), detailBold, detailNormal), mainLabelFont);
            return;
        }
        addMetaRow(table, "Comparison Sources:", "Unknown Configuration", mainLabelFont, detailNormal);
    }

    private void addRichRow(PdfPTable table, String label, PdfPCell valueCell, Font labelFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(10f);
        labelCell.setVerticalAlignment(Element.ALIGN_TOP);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private PdfPCell createFileDetailsCell(File file, Font boldFont, Font normalFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(5f);

        cell.addElement(formatLine("File:", file.getOriginalFilename(), boldFont, normalFont));
        if (file.getCaptureYear() != null) {
            cell.addElement(formatLine("Year:", String.valueOf(file.getCaptureYear()), boldFont, normalFont));
        }
        if (file.getPointCount() != null) {
            String points = NumberFormat.getIntegerInstance(Locale.US).format(file.getPointCount());
            cell.addElement(formatLine("Points:", points, boldFont, normalFont));
        }
        if (file.getSystemIdentifier() != null) {
            cell.addElement(formatLine("System:", file.getSystemIdentifier(), boldFont, normalFont));
        }
        if (file.getLasVersion() != null) {
            cell.addElement(formatLine("LAS Ver:", file.getLasVersion(), boldFont, normalFont));
        }

        return cell;
    }

    private PdfPCell createFolderDetailsCell(Folder folder, Font boldFont, Font normalFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(5f);

        cell.addElement(formatLine("Folder:", folder.getName(), boldFont, normalFont));

        int fileCount = (folder.getFiles() != null) ? folder.getFiles().size() : 0;
        cell.addElement(formatLine("Contains:", fileCount + " files", boldFont, normalFont));

        return cell;
    }

    private void addMetaRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(5f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(5f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private Paragraph formatLine(String label, String value, Font boldFont, Font normalFont) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", boldFont));
        p.add(new Chunk(value, normalFont));
        p.setLeading(12f);
        return p;
    }

    private String generateUniqueReportName() {
        String uniqueFileName;
        do {
            uniqueFileName = UUID.randomUUID() + ".pdf";
        } while (Files.exists(Path.of(UPLOAD_DIRECTORY, uniqueFileName)));
        return uniqueFileName;
    }
}
