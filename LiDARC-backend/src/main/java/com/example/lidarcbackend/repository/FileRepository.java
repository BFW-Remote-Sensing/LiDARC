package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File,Long> {

  Optional<File> findFileByFilenameAndUploaded(String filename, Boolean uploaded);
}
