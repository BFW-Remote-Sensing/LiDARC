package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.model.DTO.Validator.FileNameValid;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Optional;


@Slf4j
@Profile("!development")
@Service
@RequiredArgsConstructor
public class PresignedUrlService implements IPresignedUrlService {

  private final MinioAsyncClient minioClient;

  //private final FileDao fileDao;

  private final MinioProperties minioProperties;

  private final UrlRepository urlRepository;
  private final FileRepository fileRepository;


  private final UrlMapper urlMapper;


  @PostConstruct
  public void init() throws MinioException, GeneralSecurityException, IOException {
    //store a base file if it doesn't already exist
    if(IPresignedUrlService.bucketExists(minioClient, minioProperties.getBucket()).join()) {
      // Bucket exists
      log.info("Bucket {} exists and is reachable" , minioProperties.getBucket());
    } else {
      throw new MinioException("Bucket does not exist: " + minioProperties.getBucket());
    }
  }


  @Override
  public Optional<FileInfoDto> fetchFileInfo(@NonNull @NotBlank  String fileName)  {
    GetPresignedObjectUrlArgs presignedObjectUrlArgs = getPresignedObjectUrlArgs(fileName, Method.GET);
    if (presignedObjectUrlArgs == null) {
      return Optional.empty();
    }
    Optional<FileInfoDto> fileInfo = getUrl(presignedObjectUrlArgs, fileName);

    if(fileInfo.isPresent()) {
      FileInfoDto fileInfoDtoActual = fileInfo.get();
      Optional<File> f = fileRepository.findFileByFilenameAndUploaded(fileName, true);
      if(f.isEmpty()) {
        return Optional.empty();
      }
      Url url = new Url();
      url.setFile(f.get());
      url.setPresignedURL(fileInfoDtoActual.getPresignedURL());
      url.setMethod(Method.GET);

      fileInfoDtoActual = urlMapper.urlToFileInfoDto(urlRepository.save(url));

      return Optional.of(fileInfoDtoActual);
    } else {
    return Optional.empty();
    }
  }

  @Override
  public Optional<FileInfoDto> fetchUploadUrl(String fileName) {
    GetPresignedObjectUrlArgs presignedObjectUrlArgs = getPresignedObjectUrlArgs(fileName, Method.PUT);
    if (presignedObjectUrlArgs == null) {
      return Optional.empty();
    }
    //check if the file already exists if not return empty
    if(fileRepository.findFileByFilenameAndUploaded(fileName, true).isPresent()) {
     return Optional.empty();
    }

    //save empty file to repo and set uploaded to false

    File file = new File();
    file.setFilename(fileName);
    file.setUploaded(false);
    fileRepository.save(file);



    Optional<FileInfoDto> fileInfo = getUrl(presignedObjectUrlArgs, fileName);
    if(fileInfo.isPresent()) {
      Url url = new Url();
      url.setFile(file);
      url.setPresignedURL(fileInfo.get().getPresignedURL());
      url.setMethod(Method.PUT);

      urlRepository.save(url);
    }

    return fileInfo;
  }

  @Override
  public Optional<FileInfoDto> uploadFinished(@NonNull String fileName) {
    File file = fileRepository.findFileByFilenameAndUploaded(fileName, false).orElseThrow(() -> new IllegalArgumentException("File not found or already uploaded: " + fileName));
    //TODO possibly check if the file actually exists in minio or add to contract that the caller has to ensure that
    file.setUploaded(true);
    file.setUploaded_at(Instant.now());
    FileInfoDto dto = new FileInfoDto(fileRepository.save(file));
    return Optional.of(dto);
  }


  private Optional<FileInfoDto> getUrl(GetPresignedObjectUrlArgs presignedObjectUrlArgs, String fileName) {
    try {
      String url = minioClient.getPresignedObjectUrl(presignedObjectUrlArgs);
      return Optional.of(FileInfoDto.builder().fileName(minioProperties.getBaseObject()).presignedURL(url).build());
    } catch (MinioException | GeneralSecurityException | IOException e) {
      log.info("Could not fetch presigned {} URL for file: {}", presignedObjectUrlArgs.method().toString(), fileName, e);
      return Optional.empty();
    }
  }


  private GetPresignedObjectUrlArgs getPresignedObjectUrlArgs(String fileName, Method method) {
    return GetPresignedObjectUrlArgs.builder()
        .method(method)
        .bucket(minioProperties.getBucket())
        .object(fileName)
        .expiry(minioProperties.getDefaultExpiryTime())
        .build();
  }

}
