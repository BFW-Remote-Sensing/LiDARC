package com.example.lidarcbackend.model.DTO;

import com.example.lidarcbackend.model.DTO.Validator.FileNameValid;
import com.example.lidarcbackend.model.entity.File;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FileInfoDto {

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("presignedURL")
    @FileNameValid
    private String presignedURL;

    @JsonProperty("uploaded")
    private Boolean uploaded;

    public FileInfoDto() {
    }

    public FileInfoDto(File file) {
        this.fileName = file.getFilename();
        this.presignedURL = null;
        this.uploaded = file.getUploaded();
    }

    public FileInfoDto(String fileName, String presignedURL, Boolean uploaded) {
        this.fileName = fileName;
        this.presignedURL = presignedURL;
        this.uploaded = uploaded;
    }
}

