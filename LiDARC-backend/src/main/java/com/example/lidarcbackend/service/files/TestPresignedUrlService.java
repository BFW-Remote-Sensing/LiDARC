package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.service.IJobTrackingService;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.repository.UrlRepository;


@Slf4j
@Profile("test")
@Primary
@Service
public class TestPresignedUrlService extends PresignedUrlService {
 public TestPresignedUrlService(MinioAsyncClient minioClient, MinioProperties minioProperties, UrlRepository urlRepository, FileRepository fileRepository, FolderRepository folderRepository, WorkerStartService workerStartService, UrlMapper urlMapper, IJobTrackingService jobTrackingService) {
    super(minioClient, minioProperties, urlRepository, fileRepository, folderRepository, workerStartService, urlMapper, jobTrackingService);
  }

  @Override
  public void init() throws MinioException, GeneralSecurityException, IOException {
    //create the base bucket

    boolean exists = false;
    try {
      if (!IPresignedUrlService.bucketExists(minioClient, minioProperties.getBucket()).get()) {

        exists = true;
      }
    } catch (Exception e) {
      //this will fail anyways
      log.warn("Could not check if bucket exists creating for test");
    }
    try {
      if (!exists) {
        minioClient.makeBucket(
            MakeBucketArgs.builder()
                .bucket(minioProperties.getBucket())
                .build()
        ).get();
      }
    } catch (Exception e) {
      throw new MinioException("Could not create base bucket");
    }

  }
}
