package com.example.lidarcbackend.unit.service;

import io.minio.MinioAsyncClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import com.example.lidarcbackend.service.files.PresignedUrlService;
import com.example.lidarcbackend.service.files.WorkerStartService;


@ExtendWith(MockitoExtension.class)
public class PresignedUrlServiceTest {
  @Mock
  private FileRepository fileRepository;

  @Mock
  private UrlRepository urlRepository;


  @Mock
  private MinioAsyncClient minioClient;


  @Mock
  private MinioProperties minioProperties;

  @Mock
  private WorkerStartService workerStartService;

  @Mock
  private UrlMapper urlMapper;

  @InjectMocks
  private PresignedUrlService presignedUrlService;


  @Test
  void fetchFileInfo_returns_mappedDto_when_existing_valid_url_found() {
    // arrange
    String filename = "file1";
    File f = new File();
    f.setId(1L);
    f.setFilename(filename);
    Url url = new Url();
    url.setFile(f);
    url.setPresignedURL("http://existing");
    FileInfoDto mapped = new FileInfoDto();
    when(urlRepository.findByFile_Filename_AndMethod_AndExpiresAtAfter(eq(filename), eq(Method.GET), any(Instant.class)))
        .thenReturn(List.of(url));
    when(urlMapper.urlToFileInfoDto(url)).thenReturn(mapped);

    // act
    Optional<FileInfoDto> res = presignedUrlService.fetchFileInfo(filename, "orig");

    // assert
    assertThat(res).isPresent();
    assertThat(res.get()).isSameAs(mapped);
    verify(urlRepository, never()).save(any());
  }


  @Test
  void fetchFileInfo_generates_and_saves_url_when_none_found() throws Exception {
    // arrange
    String filename = "file2";
    when(urlRepository.findByFile_Filename_AndMethod_AndExpiresAtAfter(eq(filename), eq(Method.GET), any(Instant.class)))
        .thenReturn(Collections.emptyList());
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://generated");
    File file = new File();
    file.setId(1L);
    file.setFilename("file");
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(true))).thenReturn(Optional.of(file));
    // capture saved Url
    when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(urlMapper.urlToFileInfoDto(any(Url.class))).thenAnswer(invocation -> {
      Url u = invocation.getArgument(0);
      return new FileInfoDto(u.getFile().getFilename(), u.getPresignedURL(), true, u.getExpiresAt());
    });
    // act
    Optional<FileInfoDto> res = presignedUrlService.fetchFileInfo(filename, "orig");

    // assert
    assertThat(res).isPresent();
    verify(urlRepository).save(any(Url.class));
  }


  @Test
  void fetchUploadUrl_returns_empty_when_file_already_uploaded() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
    // arrange
    String filename = "file3";
    File existing = new File();
    existing.setId(5L);
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(true))).thenReturn(Optional.of(existing));

    // act
    Optional<FileInfoDto> res = presignedUrlService.fetchUploadUrl(filename, "orig", null);

    // assert
    assertThat(res).isEmpty();
    verify(minioClient, never()).getPresignedObjectUrl(any());
  }


  @Test
  void uploadFinished_succeeds_and_deletes_upload_urls() throws Exception {
    // arrange
    String filename = "file4";
    File file = new File();
    file.setId(10L);
    file.setFilename(filename);
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(false))).thenReturn(Optional.of(file));
    when(fileRepository.save(file)).thenReturn(file);
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://getafterupload");
    // simulate statObject not throwing to indicate existence
    when(minioClient.statObject(any())).thenReturn(null);
    // prepare a mocked FileInfoDto parameter
    FileInfoDto body = mock(FileInfoDto.class);
    when(body.getFileName()).thenReturn(filename);
    when(body.getOriginalFileName()).thenReturn("orig");


    // act
    Optional<FileInfoDto> res = presignedUrlService.uploadFinished(body);

    // assert
    assertThat(res).isPresent();
    verify(fileRepository).save(any(File.class));
    verify(urlRepository).deleteByFileIdAndMethod(eq(file.getId()), eq(Method.PUT));
    verify(workerStartService).startMetadataJob(any());
  }
}
