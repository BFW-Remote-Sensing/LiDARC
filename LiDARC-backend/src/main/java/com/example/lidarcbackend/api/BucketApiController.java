package com.example.lidarcbackend.api;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.service.files.IPresignedUrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;

@RestController
@Slf4j
public class BucketApiController implements BucketApi {


  private final ObjectMapper objectMapper;

  private final HttpServletRequest request;

  @Autowired
  private final IPresignedUrlService presignedUrlService;

  @Autowired
  public BucketApiController(ObjectMapper objectMapper, HttpServletRequest request, IPresignedUrlService presignedUrlService) {
    this.objectMapper = objectMapper;
    this.request = request;
    this.presignedUrlService = presignedUrlService;
  }

  public ResponseEntity<FileInfoDto> fetchFile(
      @Parameter(in = ParameterIn.DEFAULT, description = "Fetch a presigned url for a specific file name from the bucket.", required = true, schema = @Schema())
      @Valid
      @RequestBody FileInfoDto body
  ) {
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains("application/json")) {
      Optional<FileInfoDto> file = presignedUrlService.fetchFileInfo( body.getFileName());
      return file.map(fileInfo -> new ResponseEntity<>(fileInfo, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    return new ResponseEntity<FileInfoDto>(HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public ResponseEntity<FileInfoDto> fetchURLForUpload(FileInfoDto body) {
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains("application/json")) {
      Optional<FileInfoDto> file = presignedUrlService.fetchUploadUrl( body.getFileName());
      return file.map(fileInfo -> new ResponseEntity<>(fileInfo, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.CONFLICT));
    }

    return new ResponseEntity<FileInfoDto>(HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public ResponseEntity<FileInfoDto> uploadFinished(FileInfoDto body) {
    return new ResponseEntity<FileInfoDto>(HttpStatus.NOT_IMPLEMENTED);
  }

}
