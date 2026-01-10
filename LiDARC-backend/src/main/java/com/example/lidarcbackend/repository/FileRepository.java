package com.example.lidarcbackend.repository;

import java.util.List;
import java.util.Optional;

import com.example.lidarcbackend.api.metadata.dtos.ComparableProjection;
import com.example.lidarcbackend.model.entity.Folder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    Page<File> findPagedByFolderIsNullAndOriginalFilenameContainingIgnoreCase(
            String originalFilename,
            Pageable pageable
    );

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
                f.created_at AS sort_ts,
                f.name AS folder_name,
                NULL AS file_name
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
                fi.uploaded_at AS sort_ts,
                NULL AS folder_name,
                fi.original_filename AS file_name
            FROM files fi
            WHERE fi.folder_id IS NULL
        ) t
        WHERE (:search IS NULL
               OR LOWER(t.folder_name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.file_name) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY t.sort_ts DESC
        """,
            countQuery = """
        SELECT COUNT(*)
        FROM (
            SELECT
                f.id AS folder_id,
                NULL AS file_id,
                f.created_at AS sort_ts,
                f.name AS folder_name,
                NULL AS file_name
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
                fi.uploaded_at AS sort_ts,
                NULL AS folder_name,
                fi.original_filename AS file_name
            FROM files fi
            WHERE fi.folder_id IS NULL
        ) t
        WHERE (:search IS NULL
               OR LOWER(t.folder_name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.file_name) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
            nativeQuery = true
    )
    Page<ComparableProjection> findComparables(@Param("search") String search, Pageable pageable);



    @Modifying
    @Query("""
        UPDATE File m
        SET m.folder = :folder
        WHERE m.id IN :metadataIds
    """)
    void updateFolderForMetadata(
            @Param("metadataIds") List<Long> metadataIds,
            @Param("folder") Folder folder
    );
}
