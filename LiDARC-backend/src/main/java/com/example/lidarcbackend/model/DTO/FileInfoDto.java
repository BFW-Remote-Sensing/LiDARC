package com.example.lidarcbackend.model.DTO;

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
    private String presignedURL;

    @JsonProperty("uploaded")
    private Boolean uploaded;

    public FileInfoDto() {
    }

    public FileInfoDto(String fileName, String presignedURL, Boolean uploaded) {
        this.fileName = fileName;
        this.presignedURL = presignedURL;
        this.uploaded = uploaded;
    }
}

