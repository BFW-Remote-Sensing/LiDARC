package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface UrlRepository extends JpaRepository<Url,Long> {

    /**
     * Find all Url records for a specific File entity.
     */
    List<Url> findByFile(File file);

    /**
     * Find the most recently created Url for a File (useful to get the active/last presigned URL).
     */
    Optional<Url> findFirstByFileOrderByCreatedAtDesc(File file);

    /**
     * Find by file id directly.
     */
    Optional<Url> findByFileId(Long fileId);

    /**
     * Find all Urls for a specific bucket and filename.
     */
    List<Url> findByBucketAndFile_Filename(String bucket, String filename);

    /**
     * Check whether a URL record exists for given bucket and filename.
     */
    boolean existsByBucketAndFile_Filename(String bucket, String filename);

    /**
     * Delete all Url records belonging to a file id.
     */
    void deleteByFileId(Long fileId);

}
