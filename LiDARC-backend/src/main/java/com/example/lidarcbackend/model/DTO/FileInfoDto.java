package com.example.lidarcbackend.model.DTO;

import com.example.lidarcbackend.model.DTO.Validator.FileNameValid;
import com.example.lidarcbackend.model.entity.File;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class FileInfoDto {

  @JsonProperty("fileName")
  @NonNull
  @FileNameValid
  private String fileName;

  @JsonProperty("presignedURL")
  private String presignedURL;

  @JsonProperty("uploaded")
  private Boolean uploaded;

  @JsonProperty("urlExpiresAt")
  private Instant urlExpiresAt;public FileInfoDto() {
  }

  public FileInfoDto(File file) {
    this.fileName = file.getFilename();
    this.presignedURL = null;
    this.uploaded = file.getUploaded();
  this.urlExpiresAt = null;
  }

  FileInfoDto(String fileName, String presignedURL, Boolean uploaded, Instant urlExpiresAt) {
    this.fileName = fileName;
    this.presignedURL = presignedURL;
    this.uploaded = uploaded;
  this.urlExpiresAt = urlExpiresAt;
  }
}

