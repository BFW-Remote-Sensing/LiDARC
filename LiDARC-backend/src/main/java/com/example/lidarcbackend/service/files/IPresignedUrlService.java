package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.model.FileInfo;

import java.util.Optional;

public interface IPresignedUrlService {

    /**
     * Fetch file information for the given file name.
     *
     * Contract:
     * - Input: non-null, non-empty fileName.
     * - Output: Optional containing a populated FileInfo (fileName, presignedURL, uploaded)
     *           or Optional.empty() if the file isn't available.
     * - Error modes: do not throw for not-found; throw IllegalArgumentException for invalid input.
     *
     * @param fileName the name of the file to look up
     * @return Optional with FileInfo if present
     */
    Optional<FileInfo> fetchFileInfo(String fileName);

    /**
     * Create or refresh a presigned URL for the provided file information.
     *
     * Contract:
     * - Input: FileInfo with fileName set (other fields optional).
     * - Output: FileInfo populated with presignedURL and uploaded flag.
     * - Error modes: throws IllegalArgumentException if fileName is missing.
     *
     * @param request the request containing at least the fileName
     * @return populated FileInfo with presignedURL
     */
    FileInfo createOrRefreshPresignedUrl(FileInfo request);

}
