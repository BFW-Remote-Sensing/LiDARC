package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.ComparisonFilePK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComparisonFileRepository extends JpaRepository<ComparisonFile, ComparisonFilePK> {
    @Query("SELECT cfs FROM ComparisonFile cfs WHERE cfs.comparisonId = :comparisonId AND cfs.fileId = :fileId")
    Optional<ComparisonFile> findComparisonFiles(Long comparisonId, Long fileId);

    @Query("SELECT cf.fileId FROM ComparisonFile cf WHERE cf.comparisonId = :comparisonId")
    List<Long> getComparisonFilesByComparisonId(Long comparisonId);

    List<ComparisonFile> findAllByComparisonIdAndIncludedTrue(Long comparisonId);

    @Query("""
        SELECT CASE WHEN COUNT(cf) = 0 THEN true ELSE false END 
        FROM ComparisonFile cf 
        WHERE cf.comparisonId = :comparisonId 
        AND cf.included = true 
        AND (cf.bucket IS NULL OR cf.objectKey IS NULL)
    """)
    boolean areAllIncludedFilesReady(@Param("comparisonId") Long comparisonId);


}
