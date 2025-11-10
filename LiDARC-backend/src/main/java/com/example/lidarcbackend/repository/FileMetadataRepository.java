package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

}
