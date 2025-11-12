package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import io.minio.BucketExistsArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import jakarta.validation.constraints.NotBlank;
import lombok.NonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IPresignedUrlService {

    /**
     * Fetch file information for the given file name.
     *
     * Contract:
     * - Input: non-null, non-empty fileName.
     * - Output: Optional containing a populated FileInfoDto (fileName, presignedURL, uploaded)
     *           or Optional.empty() if the file isn't available.
     * - Error modes: do not throw for not-found; throw IllegalArgumentException for invalid input.
     *
     * @param fileName the name of the file to look up
     * @return Optional with FileInfoDto if present
     */
    Optional<FileInfoDto> fetchFileInfo(String fileName);


  /**   * Fetches a presigned URL for uploading a file with the given name.
   *
   * Contract:
   * - Input: non-null, non-empty fileName.
   * - Output: Optional containing a populated FileInfoDto (fileName, presignedURL, uploaded=false)
   *           or Optional.empty() if the upload URL could not be generated.
   * - Error modes: do not throw for not-found; throw IllegalArgumentException for invalid input.
   *
   * @param fileName the name of the file to be uploaded
   * @return Optional with FileInfoDto if upload URL is generated
   */
  Optional<FileInfoDto> fetchUploadUrl(String fileName);

  /**
   * Marks the upload as finished for the given file name.
   *
   * Contract:
   * - Input: non-null, non-empty fileName.
   * - Output: Optional containing a populated FileInfoDto (fileName, presignedURL, uploaded=true)
   *           or Optional.empty() if the file upload could not be confirmed.
   * - Error modes: throw IllegalArgumentException for invalid input e.g. file already uploaded.
   *
   * @param fileName the name of the file that has been uploaded
   * @return Optional with FileInfoDto if upload is confirmed
   */
  Optional<FileInfoDto> uploadFinished(@NonNull @NotBlank String fileName);


   static CompletableFuture<Boolean> bucketExists(MinioAsyncClient client, String bucketName) throws MinioException, GeneralSecurityException, IOException {
    BucketExistsArgs args = BucketExistsArgs.builder().bucket(bucketName).build();
    return client.bucketExists(args);
  }

}
