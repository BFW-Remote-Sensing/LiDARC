package com.example.lidarcbackend.unit.service;

import com.example.lidarcbackend.model.entity.CoordinateSystem;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.repository.CoordinateSystemRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.service.files.MetadataService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MetadataServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private CoordinateSystemRepository coordinateSystemRepository;

    @Mock
    private Validator validator;

    @InjectMocks
    private MetadataService metadataService;

    private File existingFile;

    @BeforeEach
    public void setUp() {
        existingFile = new File();
        existingFile.setFilename("graz2021_block6_060_065_elv.las");

        lenient().when(fileRepository.findFileByFilename(any()))
                .thenReturn(Optional.of(existingFile));

    }

    private Map<String, Object> validMetadata(Map<String, Object> overrides) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("filename", "graz2021_block6_060_065_elv.las");
        metadata.put("capture_year", 2021);
        metadata.put("size_bytes", 12345L);
        metadata.put("min_x", 0.0);
        metadata.put("min_y", 0.0);
        metadata.put("min_z", 5.0);
        metadata.put("max_x", 10.0);
        metadata.put("max_y", 10.0);
        metadata.put("max_z", 20.0);
        metadata.put("point_count", 100L);
        metadata.put("las_version", "1.4");
        metadata.put("capture_software", "bfwLasProcessing;MatchT 8.0");
        metadata.put("system_identifier", "31256;austria2022");
        metadata.put("file_creation_date", "2024-03-14");
        metadata.put("coordinate_system", "EPSG:31256");

        if (overrides != null) {
            metadata.putAll(overrides);
        }

        return metadata;
    }

    private Map<String, Object> successResult(Map<String, Object> metadata) {
        return Map.of(
                "status", "success",
                "job_id", "job-123",
                "payload", Map.of("metadata", metadata, "file_name","graz2021_block6_060_065_elv.las")
        );
    }

    @Test
    void processMetadata_success_shouldSaveFile() {
        CoordinateSystem cs = new CoordinateSystem();
        cs.setAuthority("EPSG");
        cs.setCode("31256");

        when(coordinateSystemRepository.findByAuthorityAndCode("EPSG", "31256"))
                .thenReturn(Optional.of(cs));

        Map<String, Object> result = successResult(validMetadata(null));

        metadataService.processMetadata(result);

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());

        File saved = captor.getValue();
        assertEquals("graz2021_block6_060_065_elv.las", saved.getFilename());
        assertEquals((short) 2021, saved.getCaptureYear());
        assertEquals(12345L, saved.getSizeBytes());
        assertEquals(LocalDate.of(2024, 3, 14), saved.getFileCreationDate());
        assertEquals(cs, saved.getCoordinateSystem());
    }

    @Test
    void processMetadata_missingJobId_shouldNotSave() {
        Map<String, Object> result = Map.of(
                "status", "success",
                "payload", Map.of("metadata", validMetadata(null), "file_name", "graz2021_block6_060_065_elv.las")
        );

        metadataService.processMetadata(result);

        verify(fileRepository, never()).save(any());
    }

    @Test
    void processMetadata_metadataWithoutExistingFile_shouldSkipSave() {
        when(fileRepository.findFileByFilename("unknown.las"))
                .thenReturn(Optional.empty());

        metadataService.processMetadata(Map.of(
                "status", "success",
                "job_id", "job-123",
                "payload", Map.of(
                        "metadata", validMetadata(Map.of("filename", "unknown.las")),
                        "file_name", "unknown.las"
                        )
                ));

        verify(fileRepository, never()).save(any());
    }

    @Test
    void processMetadata_invalidCaptureYear_shouldIgnoreYear() {
        Map<String, Object> metadata = validMetadata(Map.of(
                "capture_year", 1800
        ));

        metadataService.processMetadata(successResult(metadata));

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());

        assertNull(captor.getValue().getCaptureYear());
    }

    @Test
    void processMetadata_nullCaptureYear_shouldSaveWithNullYear() {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("capture_year", null);

        Map<String, Object> metadata = validMetadata(overrides);

        metadataService.processMetadata(successResult(metadata));

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());

        assertNull(captor.getValue().getCaptureYear());
    }

    @Test
    void processMetadata_newCoordinateSystem_shouldBeCreated() {
        when(coordinateSystemRepository.findByAuthorityAndCode("EPSG", "9999"))
                .thenReturn(Optional.empty());

        when(coordinateSystemRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> metadata = validMetadata(Map.of(
                "coordinate_system", "EPSG:9999"
        ));

        metadataService.processMetadata(successResult(metadata));

        verify(coordinateSystemRepository).save(any(CoordinateSystem.class));
        verify(fileRepository).save(any(File.class));
    }

    @Test
    void processMetadata_errorStatus_shouldNotSave() {
        Map<String, Object> result = Map.of(
                "status", "error",
                "job_id", "job-123",
                "payload", Map.of("msg", "worker failed", "file_name", "graz2021_block6_060_065_elv.las")
        );

        metadataService.processMetadata(result);
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());

        File saved = captor.getValue();
        assertEquals("graz2021_block6_060_065_elv.las", saved.getFilename());
        assertEquals("worker failed", saved.getErrorMsg());
    }

}
