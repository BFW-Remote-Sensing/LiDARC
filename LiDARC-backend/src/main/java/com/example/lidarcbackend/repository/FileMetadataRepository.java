package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {


    /**
     * Finds file by its name
     *
     * @param filename the filename of the file
     * @return Optional containing the matching file entity if found,
     *  *         or an empty Optional if no matching entity exists
     */
    Optional<FileMetadata> findByFilename(String filename);
}
