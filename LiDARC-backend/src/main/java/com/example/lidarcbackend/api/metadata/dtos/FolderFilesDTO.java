package com.example.lidarcbackend.api.metadata.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public final class FolderFilesDTO implements ComparableItemDTO {

    private Long id;
    private String folderName;
    private Instant createdDate;
    private String status;
    private List<FileMetadataDTO> files;

    public FolderFilesDTO() {
    }
}
