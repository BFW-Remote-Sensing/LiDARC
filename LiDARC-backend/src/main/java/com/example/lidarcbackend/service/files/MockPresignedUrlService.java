package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Profile("development")
@RequiredArgsConstructor
@Slf4j
public class MockPresignedUrlService implements IPresignedUrlService {

  private static FileInfoDto baseFile;
  private final MinioClient minioClient;
  private final MinioProperties minioProperties;
  // Use a read/write lock to allow concurrent reads of the cached baseFile and safe refresh updates
  private final ReentrantReadWriteLock baseFileLock = new ReentrantReadWriteLock();

  @PostConstruct
  public void init() throws MinioException {
    //store a base file if it doesn't already exist

    try {
      if (bucketExists()) {
        if (!baseObjectExists()) {
          boolean success = uploadBaseFile();
          if (!success) {
            throw new MinioException("Could not initialize base file in bucket");
          }
        }
        //at this point we either uploaded it or it was already uploaded so we can fetch it
        log.info("Successfully initialized base file");
        baseFile = getBaseFile();
      }
    } catch (MinioException | GeneralSecurityException | IOException e) {
      log.error("Initialization of Minio failed", e);
      throw new MinioException(e.getMessage(), Arrays.toString(e.getStackTrace()));
    }
  }

  // Refresh the cached baseFile periodically so presigned URL doesn't expire.
  // Uses the scheduled thread pool from Spring.
  @Scheduled(fixedDelayString = "${minio.defaultRefresh:10}") //default 10 min, always needs to be less than the url validity
  public void refreshBaseFile() {
    // Try to refresh and recover silently on errors so scheduler keeps running
    try {
      if (!bucketExists()) {
        log.warn("Bucket '{}' does not exist during scheduled refresh", minioProperties.getBucket());
        return;
      }
      FileInfoDto refreshed = getBaseFile();
      if (refreshed != null) {
        baseFileLock.writeLock().lock();
        try {
          baseFile = refreshed;
        } finally {
          baseFileLock.writeLock().unlock();
        }
        log.debug("Refreshed baseFile cache successfully");
      }
    } catch (Exception e) {
      log.error("Scheduled refresh of baseFile failed", e);
    }
  }


  /**
   *
   * @return true if the upload was successful
   * @throws MinioException
   * @throws GeneralSecurityException
   * @throws IOException
   */
   private boolean uploadBaseFile() throws MinioException, GeneralSecurityException, IOException {
    try {
      UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
          .bucket(minioProperties.getBucket())
          .object(minioProperties.getBaseObject())
          .filename(minioProperties.getBaseObjectPath())
          .build();
      ObjectWriteResponse response = minioClient.uploadObject(uploadObjectArgs);
      //TODO change metadata and save

      return true;
    } catch (IOException e) {
      log.error("Could not find base object");
    }
    return false;
  }


  private FileInfoDto getBaseFile() throws MinioException, GeneralSecurityException, IOException {
    GetPresignedObjectUrlArgs presignedObjectUrlArgs = GetPresignedObjectUrlArgs.builder()
        .method(Method.GET)
        .bucket(minioProperties.getBucket())
        .object(minioProperties.getBaseObject())
        .expiry(minioProperties.getDefaultExpiryTime())
        .build();

    String url = minioClient.getPresignedObjectUrl(presignedObjectUrlArgs);
    return FileInfoDto.builder().fileName(minioProperties.getBaseObject()).presignedURL(url).build();
  }


  private boolean bucketExists() throws MinioException, GeneralSecurityException, IOException {
    BucketExistsArgs args = BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build();
    return minioClient.bucketExists(args);
  }

  private boolean baseObjectExists() throws MinioException, GeneralSecurityException, IOException {
    StatObjectArgs args = StatObjectArgs.builder()
        .bucket(minioProperties.getBucket())
        .object(minioProperties.getBaseObject())
        .build();
    try {
      StatObjectResponse objectResponse = minioClient.statObject(args);
      return true;
    } catch (ErrorResponseException | MinioException e) {
      log.info("Base Object does not exist in bucket");
    }
    return false;
  }


  @Override
  public Optional<FileInfoDto> fetchFileInfo(String fileName) {
    baseFileLock.readLock().lock();
    try {
      if (baseFile != null) {
        return Optional.of(baseFile);
      }
    } finally {
      baseFileLock.readLock().unlock();
    }
    return Optional.empty();
  }

  @Override
  public Optional<FileInfoDto> fetchUploadUrl(String fileName) {
    // Mock implementation: return a dummy presigned URL for upload
    String dummyUploadUrl = "https://mock-storage-service.com/upload/" + fileName + "?signature=mockSignature";
    return Optional.of(FileInfoDto.builder()
        .fileName(fileName)
        .presignedURL(dummyUploadUrl)
        .uploaded(false)
        .build());
  }

  @Override
  public Optional<FileInfoDto> uploadFinished(@NonNull FileInfoDto fileInfoDto ) {
    return Optional.empty();
  }


}
