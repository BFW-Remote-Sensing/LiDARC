package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository  extends JpaRepository<Folder, Long> {
    @Query("SELECT f FROM Folder f WHERE f.active = true " +
            "AND NOT EXISTS (SELECT 1 FROM ComparisonFolder cf WHERE cf.folderId = f.id)")
    List<Folder> findAllActiveAndUncompared();
}
