package com.example.lidarcbackend;

import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.impl.UrlMapperImpl;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.service.files.PresignedUrlService;
import com.example.lidarcbackend.service.files.WorkerStartService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.impl.UrlMapperImpl;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.service.files.PresignedUrlService;
import com.example.lidarcbackend.service.files.WorkerStartService;

//this is more or less an integration test since it uses real repositories from AbstractUnitTest

public class PresignedUrlIntegrationTests extends AbstractIntegrationTests {

  private MinioAsyncClient minioClient;
  private MinioProperties minioProperties;
  private UrlMapperImpl urlMapper;
  private PresignedUrlService presignedUrlService;
  private WorkerStartService workerStartService;

  @BeforeEach
  void setUpService() {
    minioClient = mock(MinioAsyncClient.class);
    minioProperties = new MinioProperties();
    minioProperties.setBucket("basebucket");
    minioProperties.setBaseObject("baseobject");
    minioProperties.setDefaultExpiryTime(60);
    urlMapper = new UrlMapperImpl();

    // create service instance with real repositories from AbstractUnitTest
    presignedUrlService = new PresignedUrlService(
        minioClient,
        minioProperties,
        urlRepository,
        fileRepository,
        folderRepository,
        workerStartService,
        urlMapper
    );
  }

  @Test
  void fetchUploadUrl_whenFileDoesNotExist_returnsSomeURL() throws Exception {
    // arrange
    String fileName = "upload-me.txt";
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://upload-url");
    when(minioClient.bucketExists(any())).thenReturn(CompletableFuture.completedFuture(true));

    // ensure no file exists
    assertThat(fileRepository.findFileByFilenameAndUploaded(fileName, true)).isEmpty();

    // act
    Optional<FileInfoDto> result = presignedUrlService.fetchUploadUrl(fileName, "", null);
    assertThat(result).isPresent();
    assertThat(result.get().getPresignedURL()).isEqualTo("http://upload-url");
  }

  @Test
  void fetchUploadUrl_whenFileAlreadyUploaded_returnsEmpty() {
    // arrange
    String fileName = "already.txt";
    File f = new File();
    f.setStatus("UPLOADED");
    f.setFilename(fileName);
    f.setUploaded(true);
    fileRepository.save(f);

    // act
    Optional<FileInfoDto> result = presignedUrlService.fetchUploadUrl(fileName, "", null);

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  void fetchFileInfo_whenFileExistsAndUploaded_returnsPresignedAndSavesUrl() throws Exception {
    // arrange
    String fileName = "get-me.txt";
    File f = new File();
    f.setFilename(fileName);
    f.setUploaded(true);
    f.setStatus("UPLOADED");
    fileRepository.save(f);

    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://get-url");
    when(minioClient.bucketExists(any())).thenReturn(CompletableFuture.completedFuture(true));

    // act
    Optional<FileInfoDto> result = presignedUrlService.fetchFileInfo(fileName, "");

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getPresignedURL()).isEqualTo("http://get-url");

    // url saved with GET
    Url savedUrl = urlRepository.findAll().stream().filter(u -> u.getFile().getFilename().equals(fileName)).findFirst().orElse(null);
    assertThat(savedUrl).isNotNull();
    assertThat(savedUrl.getMethod()).isEqualTo(Method.GET);
  }

  @Test
  void init_whenBucketDoesNotExist_throwsMinioException() throws Exception {
    when(minioClient.bucketExists(any())).thenReturn(CompletableFuture.completedFuture(false));

    assertThatThrownBy(() -> presignedUrlService.init()).isInstanceOf(MinioException.class);
  }


}
