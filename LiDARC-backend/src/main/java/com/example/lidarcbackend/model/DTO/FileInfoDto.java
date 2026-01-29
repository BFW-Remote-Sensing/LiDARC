package com.example.lidarcbackend.model.DTO;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import java.time.Instant;
import com.example.lidarcbackend.model.DTO.Validator.FileNameValid;
import com.example.lidarcbackend.model.entity.File;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Builder
public class FileInfoDto {

	@JsonProperty("fileName")
	@NonNull
	@FileNameValid
	private String fileName;

	@JsonProperty("originalFileName")
	private String originalFileName;

	@JsonProperty("presignedURL")
	private String presignedURL;

	@JsonProperty("uploaded")
	private Boolean uploaded;

	@JsonProperty("urlExpiresAt")
	private Instant urlExpiresAt;

	@JsonProperty("folderId")
  private Long folderId;public FileInfoDto() {
	}

	public FileInfoDto(File file) {
		this.fileName = file.getFilename();
		this.presignedURL = null;
		this.uploaded = file.getUploaded();
		this.urlExpiresAt = null;
		this.originalFileName = file.getOriginalFilename();
	this.folderId = file.getFolder() != null ? file.getFolder().getId() : null;
  }

	public FileInfoDto(String fileName) {
		this.fileName = fileName;
	}

	FileInfoDto(String fileName, String presignedURL) {
		this.fileName = fileName;
		this.presignedURL = presignedURL;
	}

	FileInfoDto(String fileName, String presignedURL, Boolean uploaded) {
		this.fileName = fileName;
		this.presignedURL = presignedURL;
		this.uploaded = uploaded;
	}


	public FileInfoDto(String fileName, String presignedURL, Boolean uploaded, Instant urlExpiresAt) {
		this.fileName = fileName;
		this.presignedURL = presignedURL;
		this.uploaded = uploaded;
		this.urlExpiresAt = urlExpiresAt;
	}

	FileInfoDto(String fileName, String originalFileName, String presignedURL, Boolean uploaded, Instant urlExpiresAt) {
		this.fileName = fileName;
		this.presignedURL = presignedURL;
		this.uploaded = uploaded;
		this.urlExpiresAt = urlExpiresAt;
		this.originalFileName = originalFileName;
	}

  FileInfoDto(String fileName, String originalFileName, String presignedURL, Boolean uploaded, Instant urlExpiresAt, Long folderId) {
    this.fileName = fileName;
    this.presignedURL = presignedURL;
    this.uploaded = uploaded;
    this.urlExpiresAt = urlExpiresAt;
    this.originalFileName = originalFileName;
    this.folderId = folderId;
  }

}

