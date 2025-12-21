package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderRepository  extends JpaRepository<Folder, Long> {
}
