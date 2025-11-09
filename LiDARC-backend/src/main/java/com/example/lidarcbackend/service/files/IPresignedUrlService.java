package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import io.minio.BucketExistsArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;

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


  /**
   *
   * @param fileName of the file you want to upload
   * @return FileInfoDto containing presigned URL for upload
   */
  Optional<FileInfoDto> fetchUploadUrl(String fileName);

   static CompletableFuture<Boolean> bucketExists(MinioAsyncClient client, String bucketName) throws MinioException, GeneralSecurityException, IOException {
    BucketExistsArgs args = BucketExistsArgs.builder().bucket(bucketName).build();
    return client.bucketExists(args);
  }

}
