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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import com.example.lidarcbackend.service.files.PresignedUrlService;
import com.example.lidarcbackend.service.files.WorkerStartService;

@ExtendWith(MockitoExtension.class)
public class PresignedUrlServiceTest {
  @Mock
  private FileRepository fileRepository;

  @Mock
  private FolderRepository folderRepository;
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
    verify(urlRepository, never()).save(any(Url.class));
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
    file.setFilename(filename);
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
    assertThat(res.get().getPresignedURL()).isEqualTo("http://generated");
    verify(minioClient).getPresignedObjectUrl(any());
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


  @Test
  void fetchUploadUrl_saves_file_with_folder_when_folderId_provided() throws Exception {
    String filename = "file-folder";
    Long folderId = 9L;
    Folder folder = new Folder();
    folder.setId(folderId);
    folder.setFiles(new java.util.ArrayList<>());

    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(true))).thenReturn(Optional.empty());
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(false))).thenReturn(Optional.empty());
    when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
    when(fileRepository.save(any(File.class))).thenAnswer(inv -> {
      File f = inv.getArgument(0);
      f.setId(20L);
      return f;
    });
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://put");
    when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<FileInfoDto> res = presignedUrlService.fetchUploadUrl(filename, "orig", folderId);

    assertThat(res).isPresent();
    assertThat(res.get().getFolderId()).isEqualTo(folderId);

    ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(fileRepository).save(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getFolder()).isSameAs(folder);
  }

  @Test
  void fetchUploadUrl_saves_file_without_folder_when_folderId_null() throws Exception {
    String filename = "file-no-folder";

    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(true))).thenReturn(Optional.empty());
    when(fileRepository.findFileByFilenameAndUploaded(eq(filename), eq(false))).thenReturn(Optional.empty());
    when(fileRepository.save(any(File.class))).thenAnswer(inv -> {
      File f = inv.getArgument(0);
      f.setId(30L);
      return f;
    });
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://put");
    when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<FileInfoDto> res = presignedUrlService.fetchUploadUrl(filename, "orig", null);

    assertThat(res).isPresent();
    assertThat(res.get().getFolderId()).isNull();

    ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(fileRepository).save(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getFolder()).isNull();
  }

  @Test
  void invalidateUrls_deletes_expired_url_without_file() {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);
    Url expiredUrl = new Url();
    expiredUrl.setId(1L);
    expiredUrl.setFile(null);
    expiredUrl.setExpiresAt(pastTime);

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl));

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository).delete(expiredUrl);
  }

  @Test
  void invalidateUrls_deletes_expired_url_for_non_uploaded_file() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);
    File file = new File();
    file.setId(1L);
    file.setFilename("testfile.txt");
    file.setUploaded(false);

    Url expiredUrl = new Url();
    expiredUrl.setId(2L);
    expiredUrl.setFile(file);
    expiredUrl.setMethod(Method.GET);
    expiredUrl.setExpiresAt(pastTime);

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl));

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository).delete(expiredUrl);
    verify(minioClient, never()).getPresignedObjectUrl(any());
  }

  @Test
  void invalidateUrls_refreshes_url_for_uploaded_file_with_get_method() throws Exception {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);
    File file = new File();
    file.setId(5L);
    file.setFilename("uploadedfile.txt");
    file.setOriginalFilename("uploadedfile.txt");
    file.setUploaded(true);

    Url expiredUrl = new Url();
    expiredUrl.setId(3L);
    expiredUrl.setFile(file);
    expiredUrl.setMethod(Method.GET);
    expiredUrl.setExpiresAt(pastTime);
    expiredUrl.setPresignedURL("http://old-url");

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl));
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://new-url");
    when(urlRepository.save(any(Url.class))).thenAnswer(inv -> {
      Url u = inv.getArgument(0);
      u.setId(4L);
      return u;
    });

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository).delete(expiredUrl);
    verify(minioClient).getPresignedObjectUrl(any());
    ArgumentCaptor<Url> savedUrlCaptor = ArgumentCaptor.forClass(Url.class);
    verify(urlRepository).save(savedUrlCaptor.capture());
    assertThat(savedUrlCaptor.getValue().getPresignedURL()).isEqualTo("http://new-url");
    assertThat(savedUrlCaptor.getValue().getFile()).isSameAs(file);
    assertThat(savedUrlCaptor.getValue().getMethod()).isEqualTo(Method.GET);
  }

  @Test
  void invalidateUrls_does_not_refresh_url_for_uploaded_file_with_put_method() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);
    File file = new File();
    file.setId(6L);
    file.setFilename("uploadedfile2.txt");
    file.setUploaded(true);

    Url expiredUrl = new Url();
    expiredUrl.setId(7L);
    expiredUrl.setFile(file);
    expiredUrl.setMethod(Method.PUT);
    expiredUrl.setExpiresAt(pastTime);

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl));

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository).delete(expiredUrl);
    verify(minioClient, never()).getPresignedObjectUrl(any());
  }

  @Test
  void invalidateUrls_handles_error_gracefully_when_url_generation_fails() throws Exception {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);
    File file = new File();
    file.setId(8L);
    file.setFilename("failfile.txt");
    file.setOriginalFilename("failfile.txt");
    file.setUploaded(true);

    Url expiredUrl = new Url();
    expiredUrl.setId(9L);
    expiredUrl.setFile(file);
    expiredUrl.setMethod(Method.GET);
    expiredUrl.setExpiresAt(pastTime);

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl));
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(minioClient.getPresignedObjectUrl(any())).thenThrow(new IOException("Minio connection error"));

    // act & assert - should not throw exception
    presignedUrlService.invalidateUrls();

    verify(urlRepository).delete(expiredUrl);
    verify(minioClient).getPresignedObjectUrl(any());
    verify(urlRepository, never()).save(any(Url.class));
  }

  @Test
  void invalidateUrls_processes_multiple_expired_urls() throws Exception {
    // arrange
    Instant pastTime = Instant.now().minusSeconds(60);

    File file1 = new File();
    file1.setId(10L);
    file1.setFilename("file1.txt");
    file1.setOriginalFilename("file1.txt");
    file1.setUploaded(true);

    File file2 = new File();
    file2.setId(11L);
    file2.setFilename("file2.txt");
    file2.setUploaded(false);

    Url expiredUrl1 = new Url();
    expiredUrl1.setId(12L);
    expiredUrl1.setFile(file1);
    expiredUrl1.setMethod(Method.GET);
    expiredUrl1.setExpiresAt(pastTime);

    Url expiredUrl2 = new Url();
    expiredUrl2.setId(13L);
    expiredUrl2.setFile(file2);
    expiredUrl2.setMethod(Method.PUT);
    expiredUrl2.setExpiresAt(pastTime);

    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(List.of(expiredUrl1, expiredUrl2));
    when(minioProperties.getDefaultExpiryTime()).thenReturn(3600);
    when(minioProperties.getBucket()).thenReturn("bucket");
    when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://new-url");
    when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository).delete(expiredUrl1);
    verify(urlRepository).delete(expiredUrl2);
    verify(minioClient).getPresignedObjectUrl(any());
    verify(urlRepository).save(any(Url.class));
  }

  @Test
  void invalidateUrls_does_not_process_when_no_expired_urls() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
    // arrange
    when(urlRepository.getUrlsByExpiresAtBefore(any(Instant.class))).thenReturn(Collections.emptyList());

    // act
    presignedUrlService.invalidateUrls();

    // assert
    verify(urlRepository, never()).delete(any());
    verify(minioClient, never()).getPresignedObjectUrl(any());
  }

}
