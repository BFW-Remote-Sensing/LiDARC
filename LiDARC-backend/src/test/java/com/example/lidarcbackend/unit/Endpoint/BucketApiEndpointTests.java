package com.example.lidarcbackend.unit.Endpoint;

import com.example.lidarcbackend.api.BucketApiController;
import com.example.lidarcbackend.base.BucketBase;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import com.example.lidarcbackend.service.files.IPresignedUrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(BucketApiController.class)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class BucketApiEndpointTests {

  private final ObjectMapper objectMapper = new ObjectMapper();
  @MockitoBean
  private IPresignedUrlService presignedUrlService;
  @MockitoBean
  private FileRepository fileRepository;
  @MockitoBean
  private UrlRepository urlRepository;
  @MockitoBean
  private MinioClient minioClient;
  @Mock
  private HttpServletRequest servletRequest;
  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // reset mocks to clear previous interactions
    reset(fileRepository, urlRepository, presignedUrlService, minioClient, servletRequest);

    // make the injected request appear to accept JSON for controller logic
    when(servletRequest.getHeader("Accept")).thenReturn("application/json");

    // build controller with mocked dependencies
    BucketApiController controller = new BucketApiController(objectMapper, servletRequest, presignedUrlService);

    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    // setup standalone MockMvc for the controller

    // stub repository save behavior so controller interactions don't NPE (even if not used here)
    File sampleFile = File.builder()
        .filename("test.txt")
        .uploaded(true)
        .build();

    Url sampleUrl = Url.builder()
        .bucket(BucketBase.BUCKET_NAME)
        .file(sampleFile)
        .method(Method.GET)
        .presignedURL("http://example.com/test.txt")
        .build();

    when(fileRepository.save(any(File.class))).thenReturn(sampleFile);
    when(urlRepository.save(any(Url.class))).thenReturn(sampleUrl);

    // stub presigned service if controller calls it
    when(presignedUrlService.fetchFileInfo(anyString(), anyString())).thenReturn(Optional.of(new FileInfoDto()));
    when(presignedUrlService.fetchFileInfo(anyString(), any())).thenReturn(Optional.of(new FileInfoDto()));
    when(presignedUrlService.fetchUploadUrl(anyString(), anyString())).thenReturn(java.util.Optional.of(new com.example.lidarcbackend.model.DTO.FileInfoDto()));
    when(presignedUrlService.uploadFinished(any(com.example.lidarcbackend.model.DTO.FileInfoDto.class))).thenReturn(
        java.util.Optional.of(new com.example.lidarcbackend.model.DTO.FileInfoDto()));
  }

  @Test
  void fetchFileEndpointIsMapped() throws Exception {
    String json = objectMapper.writeValueAsString(Collections.singletonMap("fileName", "test.txt"));

    MvcResult result = mockMvc.perform(
        post("/api/v1/bucket")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    // Ensure the endpoint exists (not a 404). Specific status/behavior depends on controller implementation.
    assertNotEquals(404, result.getResponse().getStatus());
  }

  @Test
  void uploadEndpointsAreMapped() throws Exception {
    String json = objectMapper.writeValueAsString(Collections.singletonMap("fileName", "test.txt"));

    MvcResult postResult = mockMvc.perform(
        post("/api/v1/bucket/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    mockMvc.perform(
        put("/api/v1/bucket/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    assertNotEquals(404, postResult.getResponse().getStatus());
  }


}
