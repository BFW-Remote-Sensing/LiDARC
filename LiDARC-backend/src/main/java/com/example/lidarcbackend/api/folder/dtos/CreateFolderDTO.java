package com.example.lidarcbackend.api.folder.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderDTO {

    @NotEmpty(message = "Folder name cannot be empty")
    private String name;

    @NotEmpty(message = "Status is required")
    @Pattern(
            regexp = "UPLOADED|PROCESSING|PROCESSED|FAILED",
            message = "Status must be one of: UPLOADED, PROCESSING, PROCESSED, FAILED"
    )
    private String status;

    @NotEmpty(message = "File IDs cannot be empty")
    private List<Long> fileIds;
}

