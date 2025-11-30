package com.example.lidarcbackend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.lidarcbackend.model.entity.File;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {

  Optional<File> findFileByFilenameAndUploaded(String filename, Boolean uploaded);

  Optional<File> findFileByOriginalFilename(String originalFilename);
}
