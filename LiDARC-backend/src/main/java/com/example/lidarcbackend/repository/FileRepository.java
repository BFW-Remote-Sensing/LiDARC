package com.example.lidarcbackend.repository;

import java.util.List;
import java.util.Optional;

import com.example.lidarcbackend.api.metadata.dtos.ComparableProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.lidarcbackend.model.entity.File;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {

    Optional<File> findFileByFilenameAndUploaded(String filename, Boolean uploaded);

    Optional<File> findFileByFilename(String filename);

    List<File> findAllByFolderIsNull(Sort sort);

    Page<File> findPagedByFolderIsNull(Pageable pageable);

    List<File> findAllByFolderId(Long folderId, Sort sort);

    List<File> findAllByFolderIdIn(
            List<Long> folderIds,
            Sort sort
    );

    List<File> findAllByFolderIsNotNull(Sort sort);

    @Query(
            value = """
                    SELECT folder_id AS folderId, file_id AS fileId
                    FROM (
                        SELECT
                            f.id AS folder_id,
                            NULL AS file_id,
                            f.created_at AS sort_ts
                        FROM folders f
                        WHERE EXISTS (
                            SELECT 1
                            FROM files fi
                            WHERE fi.folder_id = f.id
                        )
                    
                        UNION ALL
                    
                        SELECT
                            NULL AS folder_id,
                            fi.id AS file_id,
                            fi.uploaded_at AS sort_ts
                        FROM files fi
                        WHERE fi.folder_id IS NULL
                    ) t
                    ORDER BY t.sort_ts DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT f.id
                        FROM folders f
                        WHERE EXISTS (
                            SELECT 1
                            FROM files fi
                            WHERE fi.folder_id = f.id
                        )
                    
                        UNION ALL
                    
                        SELECT fi.id
                        FROM files fi
                        WHERE fi.folder_id IS NULL
                    ) cnt
                    """,
            nativeQuery = true
    )
    Page<ComparableProjection> findComparables(Pageable pageable);
}
