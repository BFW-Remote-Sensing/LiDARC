package com.example.lidarcbackend.api;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.service.files.MockPresignedUrlService;
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

  private final MockPresignedUrlService mockPresignedUrlService;

  @Autowired
  public BucketApiController(ObjectMapper objectMapper, HttpServletRequest request, MockPresignedUrlService mockPresignedUrlService) {
    this.objectMapper = objectMapper;
    this.request = request;
    this.mockPresignedUrlService = mockPresignedUrlService;
  }

  public ResponseEntity<FileInfoDto> fetchFile(
      @Parameter(in = ParameterIn.DEFAULT, description = "Fetch a presigned url for a specific file name from the bucket.", required = true, schema = @Schema())
      @Valid
      @RequestBody FileInfoDto body
  ) {
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains("application/json")) {
      try {
        FileInfoDto example =
            objectMapper.readValue(
                "{\n  \"fileName\" : \"graz2021_block6_060_065_elv.laz\",\n  \"presignedURL\" : \"presignedURL\",\n  \"uploaded\" : false\n}", FileInfoDto.class);
        Optional<FileInfoDto> file = mockPresignedUrlService.fetchFileInfo(example.getFileName());
        return file.map(fileInfo -> new ResponseEntity<>(fileInfo, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
      } catch (IOException e) {
        log.error("Couldn't serialize response for content type application/json", e);
        return new ResponseEntity<FileInfoDto>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }

    return new ResponseEntity<FileInfoDto>(HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public ResponseEntity<FileInfoDto> fetchURLForUpload(FileInfoDto body) {
    return null;
  }

  @Override
  public ResponseEntity<FileInfoDto> uploadFinished(FileInfoDto body) {
    return null;
  }

}
