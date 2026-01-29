package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.ComparisonFolder;
import com.example.lidarcbackend.model.entity.ComparisonFolderPK;
import com.example.lidarcbackend.repository.projection.FileUsageCount;
import com.example.lidarcbackend.repository.projection.FolderUsageCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    boolean existsByFolderId(Long folderId);

    @Query("SELECT CASE WHEN COUNT(cf) > 0 THEN true ELSE false END FROM ComparisonFolder cf, Comparison c " +
            "WHERE cf.comparisonId = c.id AND cf.folderId = :folderId " +
            "AND c.status IN (com.example.lidarcbackend.model.entity.Comparison.Status.PREPROCESSING, " +
            "com.example.lidarcbackend.model.entity.Comparison.Status.COMPARING)")
    boolean isFolderInOngoingComparison(@Param("folderId") Long folderId);

    @Modifying
    @Query("DELETE FROM ComparisonFolder cf WHERE cf.folderId = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);

    @Modifying
    @Query("DELETE FROM ComparisonFolder cf WHERE cf.comparisonId = :comparisonId")
    void deleteByComparisonId(@Param("comparisonId") Long comparisonId);

    @Query("""
        SELECT cf.folderId AS folderId,
               (SELECT COUNT(allCf) FROM ComparisonFolder allCf WHERE allCf.folderId = cf.folderId) AS totalCount
        FROM ComparisonFolder cf, Folder f
        WHERE cf.folderId = f.id
          AND cf.comparisonId = :comparisonId
          AND f.active = false
    """)
    List<FolderUsageCount> findFolderUsageByComparisonId(@Param("comparisonId") Long comparisonId);
}
