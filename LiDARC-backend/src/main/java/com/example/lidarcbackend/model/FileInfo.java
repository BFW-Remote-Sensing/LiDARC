package com.example.lidarcbackend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public class FileInfo {

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("presignedURL")
    private String presignedURL;

    @JsonProperty("uploaded")
    private Boolean uploaded;

    public FileInfo() {
    }

    public FileInfo(String fileName, String presignedURL, Boolean uploaded) {
        this.fileName = fileName;
        this.presignedURL = presignedURL;
        this.uploaded = uploaded;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPresignedURL() {
        return presignedURL;
    }

    public void setPresignedURL(String presignedURL) {
        this.presignedURL = presignedURL;
    }

    public Boolean getUploaded() {
        return uploaded;
    }

    public void setUploaded(Boolean uploaded) {
        this.uploaded = uploaded;
    }
}

