package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.ComparisonFilePK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComparisonFileRepository extends JpaRepository<ComparisonFile, ComparisonFilePK> {
    @Query("SELECT cfs FROM ComparisonFile cfs WHERE cfs.comparisonId = :comparisonId AND cfs.fileId = :fileId")
    Optional<ComparisonFile> findComparisonFiles(int comparisonId, int fileId);

    @Query("SELECT cf.fileId FROM ComparisonFile cf WHERE cf.comparisonId = :comparisonId")
    List<Long> getComparisonFilesByComparisonId(Long comparisonId);

    @Query("SELECT cf FROM ComparisonFile cf WHERE cf.comparisonId = :comparisonId")
    List<ComparisonFile> findAllByComparisonId(@Param("comparisonId") int comparisonId);
}
