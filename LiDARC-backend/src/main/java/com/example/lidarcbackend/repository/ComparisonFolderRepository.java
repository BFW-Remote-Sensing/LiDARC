package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.ComparisonFolder;
import com.example.lidarcbackend.model.entity.ComparisonFolderPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComparisonFolderRepository extends JpaRepository<ComparisonFolder, ComparisonFolderPK> {
    @Query("SELECT cfs FROM ComparisonFolder cfs WHERE cfs.comparisonId = :comparisonId AND cfs.folderId = :folderId")
    Optional<ComparisonFolder> findComparisonFolders(Long comparisonId, Long folderId);

    @Query("SELECT cf.folderId FROM ComparisonFolder cf WHERE cf.comparisonId = :comparisonId")
    List<Long> getComparisonFoldersByComparisonId(Long comparisonId);

    @Query("SELECT cf FROM ComparisonFolder cf WHERE cf.comparisonId = :comparisonId")
    List<ComparisonFolder> findAllByComparisonId(@Param("comparisonId") int comparisonId);
}
