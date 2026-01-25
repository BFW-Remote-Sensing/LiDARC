package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.ComparisonFilePK;
import com.example.lidarcbackend.repository.projection.FileUsageCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
        SELECT cf.fileId AS fileId,
               (SELECT COUNT(allCf) FROM ComparisonFile allCf WHERE allCf.fileId = cf.fileId) AS totalCount
        FROM ComparisonFile cf, File f
        WHERE cf.fileId = f.id
          AND cf.comparisonId = :comparisonId
          AND f.active = false
    """)
    List<FileUsageCount> findFileUsageByComparisonId(@Param("comparisonId") Long comparisonId);

    @Query("SELECT CASE WHEN COUNT(cf) > 0 THEN true ELSE false END FROM ComparisonFile cf, Comparison c " +
            "WHERE cf.comparisonId = c.id AND cf.fileId = :fileId " +
            "AND c.status IN (com.example.lidarcbackend.model.entity.Comparison.Status.PREPROCESSING, " +
            "com.example.lidarcbackend.model.entity.Comparison.Status.COMPARING)")
    boolean isFileInOngoingComparison(@Param("fileId") Long fileId);

    @Query("SELECT CASE WHEN COUNT(cf) > 0 THEN true ELSE false END " +
            "FROM ComparisonFile cf, Comparison c " +
            "WHERE cf.comparisonId = c.id AND cf.fileId IN :fileIds " +
            "AND c.status IN (com.example.lidarcbackend.model.entity.Comparison.Status.PREPROCESSING, " +
            "com.example.lidarcbackend.model.entity.Comparison.Status.COMPARING)")
    boolean areFilesInOngoingComparison(@Param("fileIds") List<Long> fileIds);

    @Modifying
    @Query("DELETE FROM ComparisonFile cf WHERE cf.fileId = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);

    @Modifying
    @Query("DELETE FROM ComparisonFile cf WHERE cf.comparisonId = :comparisonId")
    void deleteByComparisonId(@Param("comparisonId") Long comparisonId);

    boolean existsByFileId(Long fileId);
}
