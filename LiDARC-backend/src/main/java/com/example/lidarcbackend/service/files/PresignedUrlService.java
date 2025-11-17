package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.MinioProperties;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
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

  //minimum added time to the current time when checking for expiry
  private final int minimumAddedTime = 20;


  @Scheduled(fixedDelayString = "${minio.defaultRefresh:5000}")
  //default 10 min, always needs to be less than the url validity
  public void removeExpiredUrls() {
    // Try to refresh and recover silently on errors so scheduler keeps running
    urlRepository.findUrlByExpiresAtBefore(Instant.now().plusSeconds(minimumAddedTime)).forEach(url -> {
      log.info("Removing expired URL for file: {}", url.getFile().getFilename());
      urlRepository.delete(url);
    });
  }


  @PostConstruct
  public void init() throws MinioException, GeneralSecurityException, IOException {
    //store a base file if it doesn't already exist
    if (IPresignedUrlService.bucketExists(minioClient, minioProperties.getBucket()).join()) {
      // Bucket exists
      log.info("Bucket {} exists and is reachable", minioProperties.getBucket());
    } else {
      throw new MinioException("Bucket does not exist: " + minioProperties.getBucket());
    }
  }


  @Override
  public Optional<FileInfoDto> fetchFileInfo(@NonNull @NotBlank String fileName) {
    List<Url> presignedUrls = urlRepository.findByFile_Filename_AndMethod_AndExpiresAtAfter(fileName, Method.GET, Instant.now().plusSeconds(minimumAddedTime));
    if (!presignedUrls.isEmpty()) {
      Url url = presignedUrls.getFirst();
      FileInfoDto fileInfoDto = urlMapper.urlToFileInfoDto(url);
      return Optional.of(fileInfoDto);
    }

    GetPresignedObjectUrlArgs presignedObjectUrlArgs = getPresignedObjectUrlArgs(fileName, Method.GET);
    if (presignedObjectUrlArgs == null) {
      return Optional.empty();
    }
    //expiry should be set before fetching url
    Instant expiresAt = Instant.now().plusSeconds(minioProperties.getDefaultExpiryTime());
    Optional<FileInfoDto> fileInfo = getUrl(presignedObjectUrlArgs, fileName);

    if (fileInfo.isPresent()) {
      FileInfoDto fileInfoDtoActual = fileInfo.get();
      Optional<File> f = fileRepository.findFileByFilenameAndUploaded(fileName, true);
      if (f.isEmpty()) {
        return Optional.empty();
      }
      Url url = new Url();
      url.setFile(f.get());
      url.setPresignedURL(fileInfoDtoActual.getPresignedURL());
      url.setMethod(Method.GET);
      url.setCreatedAt(Instant.now());
      url.setExpiresAt(expiresAt);

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
    if (fileRepository.findFileByFilenameAndUploaded(fileName, true).isPresent()) {
      return Optional.empty();
    }

    Instant expiresAt = Instant.now().plusSeconds(minioProperties.getDefaultExpiryTime());

    Optional<File> fileOpt = fileRepository.findFileByFilenameAndUploaded(fileName, false);
    File file;
    if (fileOpt.isPresent()) {
      //file already exists but not yet uploaded
      //check whether there is a valid upload url
      List<Url> presignedUrls = urlRepository.findByFile_Filename_AndMethod_AndExpiresAtAfter(fileName, Method.PUT, Instant.now());
      Optional<Url> createdUpload = presignedUrls.stream().findAny();
      if (createdUpload.isPresent()) {
        FileInfoDto fileInfo;
        fileInfo = new FileInfoDto(fileOpt.get());
        fileInfo.setPresignedURL(createdUpload.get().getPresignedURL());
        fileInfo.setUploaded(false);
        return Optional.of(fileInfo);
      }

      file = fileOpt.get();
    } else {
      file = new File();
      file.setFilename(fileName);
      file.setUploaded(false);
      file = fileRepository.save(file);
    }

    Optional<FileInfoDto> fOpt = getUrl(presignedObjectUrlArgs, fileName);
    if (fOpt.isPresent()) {
      Url url = new Url();

      FileInfoDto fileInfo = new FileInfoDto();
      fileInfo.setFileName(fileName);
      fileInfo = fOpt.get();
      fileInfo.setUrlExpiresAt(expiresAt);
      url.setFile(file);
      url.setPresignedURL(fOpt.get().getPresignedURL());
      url.setMethod(Method.PUT);
      url.setExpiresAt(expiresAt);
      url.setCreatedAt(Instant.now());
      urlRepository.save(url);
      return Optional.of(fileInfo);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<FileInfoDto> uploadFinished(@NonNull FileInfoDto body) {
    File file = fileRepository.findFileByFilenameAndUploaded(body.getFileName(), false)
        .orElseThrow(() -> new IllegalArgumentException("File not found or already uploaded: " + body.getFileName()));
    //TODO possibly check if the file actually exists in minio or add to contract that the caller has to ensure that
    file.setUploaded(true);
    file.setUploaded_at(Instant.now());
    FileInfoDto dto = new FileInfoDto(fileRepository.save(file));

    //remove old urls
    urlRepository.deleteByFileIdAndMethod(file.getId(), Method.PUT);

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
