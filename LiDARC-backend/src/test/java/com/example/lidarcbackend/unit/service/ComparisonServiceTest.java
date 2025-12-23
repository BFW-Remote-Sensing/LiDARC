package com.example.lidarcbackend.unit.service;

import com.example.lidarcbackend.api.comparison.ComparisonMapper;
import com.example.lidarcbackend.api.comparison.dtos.ComparisonDTO;
import com.example.lidarcbackend.api.comparison.dtos.CreateComparisonRequest;
import com.example.lidarcbackend.api.comparison.dtos.GridParameters;
import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.model.DTO.BoundingBox;
import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.service.files.ComparisonService;
import com.example.lidarcbackend.service.files.MetadataService;
import com.example.lidarcbackend.service.files.WorkerStartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ComparisonServiceTest {

    @Mock
    private ComparisonRepository comparisonRepository;
    @Mock
    private ComparisonFileRepository comparisonFileRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private WorkerStartService workerStartService;

    @Mock
    private MetadataService metadataService;

    @Mock
    private ComparisonMapper comparisonMapper;

    @InjectMocks
    private ComparisonService comparisonService;

    private CreateComparisonRequest createRequest;
    private Comparison savedComparison;

    @BeforeEach
    void setUp() {
        // 1. Setup default Grid (Cell Size = 2.0 to test snapping logic)
        GridParameters defaultGrid = new GridParameters();
        defaultGrid.setCellWidth(2);
        defaultGrid.setCellHeight(2);
        defaultGrid.setxMin(0.0);
        defaultGrid.setyMin(0.0);

        createRequest = new CreateComparisonRequest();
        createRequest.setGrid(defaultGrid);

        savedComparison = new Comparison();
        savedComparison.setId(999L);
        lenient().when(comparisonMapper.toEntityFromRequest(any())).thenReturn(savedComparison);
        lenient().when(comparisonRepository.save(any())).thenReturn(savedComparison);
        lenient().when(comparisonMapper.toDto(any())).thenReturn(new ComparisonDTO());
    }

    private File createFile(Long id, double xMin, double xMax, double yMin, double yMax) {
        File f = new File();
        f.setId(id);
        f.setFilename("test-file-" + id);
        f.setMinX(xMin);
        f.setMaxX(xMax);
        f.setMinY(yMin);
        f.setMaxY(yMax);
        return f;
    }

    @Test
    void saveComparison_NoOverlap_ShouldStartTwoFullJobs() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;
        File file1 = createFile(fileId1, 0.0, 10.0, 0.0, 10.0);   // 0-10
        File file2 = createFile(fileId2, 20.0, 30.0, 0.0, 10.0);  // 20-30

        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));

        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);

        // We expect exactly 2 jobs to be started
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();

        // Validate Job 1 (File 1)
        StartPreProcessJobDto job1 = jobs.getFirst();
        assertEquals(fileId1, job1.getFileId());
        assertEquals(1, job1.getBboxes().size());
        assertBoundingBox(job1.getBboxes().getFirst(), 0.0, 10.0, 0.0, 10.0);

        // Validate Job 2 (File 2)
        StartPreProcessJobDto job2 = jobs.get(1);
        assertEquals(fileId2, job2.getFileId());
        assertEquals(1, job2.getBboxes().size());
        assertBoundingBox(job2.getBboxes().getFirst(), 20.0, 30.0, 0.0, 10.0);
    }

    @Test
    void saveComparison_PartialOverlap_ShouldClipSecondFile() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;

        File file1 = createFile(fileId1, 0.0, 100.0, 0.0, 10.0);
        File file2 = createFile(fileId2, 95.0, 110.0, 0.0, 10.0);
        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));

        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();

        StartPreProcessJobDto job2 = jobs.get(1);
        assertEquals(fileId2, job2.getFileId());

        List<BoundingBox> regions = job2.getBboxes();
        assertEquals(1, regions.size(), "File 2 should be cut into exactly 1 piece (Right Strip)");
        assertBoundingBox(regions.getFirst(), 100.0, 110.0, 0.0, 10.0);
    }

    @Test
    void saveComparison_FullOverlap_ShouldSkipSecondFile() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;
        File file1 = createFile(fileId1, 0.0, 100.0, 0.0, 100.0);
        File file2 = createFile(fileId2, 20.0, 80.0, 20.0, 80.0);
        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);

        verify(workerStartService, times(1)).startPreprocessingJob(captor.capture());
        StartPreProcessJobDto job = captor.getValue();
        assertEquals(fileId1, job.getFileId(), "Only File 1 should be processed");

        assertEquals(1, job.getBboxes().size());
        assertBoundingBox(job.getBboxes().getFirst(), 0.0, 100.0, 0.0, 100.0);
    }

    @Test
    void saveComparison_CenterHoleOverlap_ShouldSplitIntoFour() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;
        File file1 = createFile(fileId1, 40.0, 60.0, 40.0, 60.0);
        File file2 = createFile(fileId2, 0.0, 100.0, 0.0, 100.0);

        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));

        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();

        StartPreProcessJobDto job2 = jobs.get(1);
        List<BoundingBox> regions = job2.getBboxes();

        assertEquals(4, regions.size(), "Should be split into Top, Bottom, Left, Right");
        // 1. Top Strip (Full Width, above hole) -> Y: 60-100, X: 0-100
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 0,100,60,100)), "Missing Top Strip");

        // 2. Bottom Strip (Full Width, below hole) -> Y: 0-40, X: 0-100
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 0, 100, 0, 40)), "Missing Bottom Strip");

        // 3. Left Strip (Height constrained to hole) -> X: 0-40, Y: 40-60
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 0, 40, 40, 60)), "Missing Left Strip");

        // 4. Right Strip (Height constrained to hole) -> X: 60-100, Y: 40-60
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 60, 100, 40, 60)),  "Missing Right Strip");
    }

    @Test
    void saveComparison_EdgeOverlap_ShouldSplitIntoThree() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;
        File file1 = createFile(fileId1, 40.0, 60.0, 50.0, 100.0);
        File file2 = createFile(fileId2, 0.0, 100.0, 0.0, 100.0);

        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());

        StartPreProcessJobDto job2 = captor.getAllValues().get(1);
        List<BoundingBox> regions = job2.getBboxes();

        assertEquals(3, regions.size(), "Should form a U-shape (Left, Right, Bottom)");

        // 1. Bottom (Full Width) -> Y: 0-50, X: 0-100
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 0, 100, 0, 50)), "Missing Bottom Base");

        // 2. Left (Height constrained) -> X: 0-40, Y: 50-100
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 0, 40, 50, 100)), "Missing Left Arm");

        // 3. Right (Height constrained) -> X: 60-100, Y: 50-100
        assertTrue(regions.stream().anyMatch(b -> isApprox(b, 60, 100, 50, 100)), "Missing Right Arm");
    }

    @Test
    void saveComparison_TopHalfOverlap_ShouldReturnBottomHalf() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;
        File file1 = createFile(fileId1, 0.0, 100.0, 50.0, 100.0);
        File file2 = createFile(fileId2, 0.0, 100.0, 0.0, 100.0);
        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));
        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());

        StartPreProcessJobDto job2 = captor.getAllValues().get(1);
        List<BoundingBox> regions = job2.getBboxes();

        assertEquals(1, regions.size());
        // Should be the bottom half (0-50 Y)
        assertBoundingBox(regions.getFirst(), 0.0, 100.0, 0.0, 50.0);
    }

    @Test
    void saveComparison_GridLargerThanFile_ShouldSnapToSingleCell() throws NotFoundException {
        Long fileId = 1L;
        // File is small (0-10)
        File file = createFile(fileId, 0.0, 10.0, 0.0, 10.0);

        // Grid is HUGE (Cell Size 100)
        GridParameters hugeGrid = new GridParameters();
        hugeGrid.setCellWidth(100);
        hugeGrid.setCellHeight(100);
        hugeGrid.setxMin(0.0);
        hugeGrid.setyMin(0.0);

        createRequest.setGrid(hugeGrid);
        createRequest.setFileMetadataIds(List.of(fileId));

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));

        comparisonService.saveComparison(createRequest, List.of(fileId));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService).startPreprocessingJob(captor.capture());

        // Expect: 0-10 snapped to 0-100 (One full cell)
        assertBoundingBox(captor.getValue().getBboxes().getFirst(), 0.0, 100.0, 0.0, 100.0);
    }

    @Test
    void saveComparison_MultipleFilesStaircase_ShouldAccumulateCuts() throws NotFoundException {
        Long id1 = 1L,  id2 = 2L, id3 = 3L,  id4 = 4L;
        File f1 = createFile(id1, 0.0, 20.0, 0.0, 10.0);
        File f2 = createFile(id2, 10.0, 40.0, 0.0, 10.0);
        File f3 = createFile(id3, 30.0, 60.0, 0.0, 10.0);
        File f4 = createFile(id4, 50.0, 80.0, 0.0, 10.0);

        when(fileRepository.findById(id1)).thenReturn(Optional.of(f1));
        when(fileRepository.findById(id2)).thenReturn(Optional.of(f2));
        when(fileRepository.findById(id3)).thenReturn(Optional.of(f3));
        when(fileRepository.findById(id4)).thenReturn(Optional.of(f4));

        List<Long> fileIds = List.of(id1, id2, id3, id4);
        createRequest.setFileMetadataIds(fileIds);

        comparisonService.saveComparison(createRequest, fileIds);
        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(4)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();

        StartPreProcessJobDto job1 = jobs.getFirst();
        assertEquals(id1, job1.getFileId());
        assertBoundingBox(job1.getBboxes().getFirst(), 0.0, 20.0, 0.0, 10.0);

        StartPreProcessJobDto job2 = jobs.get(1);
        assertEquals(id2, job2.getFileId());
        assertBoundingBox(job2.getBboxes().getFirst(), 20.0, 40.0, 0.0, 10.0);

        StartPreProcessJobDto job3 = jobs.get(2);
        assertEquals(id3, job3.getFileId());
        assertBoundingBox(job3.getBboxes().getFirst(), 40.0, 60.0, 0.0, 10.0);

        StartPreProcessJobDto job4 = jobs.get(3);
        assertEquals(id4, job4.getFileId());
        assertBoundingBox(job4.getBboxes().getFirst(), 60.0, 80.0, 0.0, 10.0);
    }

    @Test
    void saveComparison_GridSnapping_ShouldPushStartToNextEvenNumber() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;

        GridParameters gridWidth2 = new GridParameters();
        gridWidth2.setCellWidth(2);
        gridWidth2.setCellHeight(2); // Height doesn't matter here
        gridWidth2.setxMin(0.0);
        gridWidth2.setyMin(0.0);
        createRequest.setGrid(gridWidth2);

        File file1 = createFile(fileId1, 0.0, 101.0, 0.0, 10.0);
        File file2 = createFile(fileId2, 90.0, 120.0, 0.0, 10.0);
        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));

        createRequest.setFileMetadataIds(List.of(fileId1, fileId2));
        comparisonService.saveComparison(createRequest, List.of(fileId1, fileId2));

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);
        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();

        // Check File 2
        StartPreProcessJobDto job2 = jobs.get(1);
        assertEquals(fileId2, job2.getFileId());

        List<BoundingBox> regions = job2.getBboxes();
        assertEquals(1, regions.size());

        // Assert that xMin is 102.0 (Next grid line), NOT 101.0
        assertBoundingBox(regions.getFirst(), 102.0, 120.0, 0.0, 10.0);
    }

    @Test
    void saveComparison_SeparateFolders_ShouldProcessIdenticalFilesIndependently() throws NotFoundException {
        Long fileId1 = 1L;
        Long fileId2 = 2L;

        File file1 = createFile(fileId1, 0.0, 100.0, 0.0, 100.0);
        File file2 = createFile(fileId2, 0.0, 100.0, 0.0, 100.0);

        when(fileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(fileRepository.findById(fileId2)).thenReturn(Optional.of(file2));

        createRequest.setFolderAFiles(List.of(fileId1));
        createRequest.setFolderBFiles(List.of(fileId2));

        comparisonService.saveComparison(createRequest, List.of());

        ArgumentCaptor<StartPreProcessJobDto> captor = ArgumentCaptor.forClass(StartPreProcessJobDto.class);

        verify(workerStartService, times(2)).startPreprocessingJob(captor.capture());
        List<StartPreProcessJobDto> jobs = captor.getAllValues();StartPreProcessJobDto job1 = jobs.stream()
                .filter(j -> j.getFileId().equals(fileId1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Job for File 1 missing"));

        assertBoundingBox(job1.getBboxes().getFirst(), 0.0, 100.0, 0.0, 100.0);

        StartPreProcessJobDto job2 = jobs.stream()
                .filter(j -> j.getFileId().equals(fileId2))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Job for File 2 missing"));

        assertBoundingBox(job2.getBboxes().getFirst(), 0.0, 100.0, 0.0, 100.0);
    }

    private void assertBoundingBox(BoundingBox box, double xMin, double xMax, double yMin, double yMax) {
        assertEquals(xMin, box.getxMin(), "xMin mismatch");
        assertEquals(xMax, box.getxMax(), "xMax mismatch");
        assertEquals(yMin, box.getyMin(), "yMin mismatch");
        assertEquals(yMax, box.getyMax(), "yMax mismatch");
    }

    private boolean isApprox(BoundingBox b, double xMin, double xMax, double yMin, double yMax) {
        return Math.abs(b.getxMin() - xMin) == 0 &&
                Math.abs(b.getxMax() - xMax) == 0 &&
                Math.abs(b.getyMin() - yMin) == 0 &&
                Math.abs(b.getyMax() - yMax) == 0;
    }
}
