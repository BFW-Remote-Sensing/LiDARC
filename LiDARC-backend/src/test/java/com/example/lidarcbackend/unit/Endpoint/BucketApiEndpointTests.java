package com.example.lidarcbackend.unit.Endpoint;

import com.example.lidarcbackend.base.BucketBase;
import com.example.lidarcbackend.base.TestMinioConfiguration;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import com.example.lidarcbackend.service.files.IPresignedUrlService;
import com.example.lidarcbackend.unit.AbstractUnitTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest
@Import(TestMinioConfiguration.class)
public class BucketApiEndpointTests extends AbstractUnitTest {
  //TODO finish tests
  @MockitoBean
  IPresignedUrlService presignedUrlService;
  TestMinioConfiguration minioConfiguration = new TestMinioConfiguration();
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private MinioClient minioClient = minioConfiguration.minioClient();

  @BeforeEach
  void setUp() throws Exception {

    fileRepository.deleteAll();
    urlRepository.deleteAll();


    minioClient.putObject(PutObjectArgs.builder()
        .bucket(BucketBase.BUCKET_NAME)
        .object("test.txt")
        .stream(BucketBase.getTestFileStream(), -1, 10485760)
        .build());

    File f = fileRepository.save(File.builder()
        .filename("test.txt")
        .uploaded(true)
        .build());

    urlRepository.save(Url.builder()
        .bucket(BucketBase.BUCKET_NAME)
        .file(f)
        .method(Method.GET)
        .presignedURL("http://example.com/test.txt")
        .build());

  }


  @Test
  void fetchFileEndpointIsMapped() throws Exception {
    String json = objectMapper.writeValueAsString(Collections.singletonMap("fileName", "test.txt"));

    MvcResult result = mockMvc.perform(
        post("/bucket")
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
        post("/bucket/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    MvcResult putResult = mockMvc.perform(
        put("/bucket/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    assertNotEquals(404, postResult.getResponse().getStatus());
  }


  @Test
  void fetchPresignedUrl() throws Exception {
    String json = objectMapper.writeValueAsString(Collections.singletonMap("fileName", "test.txt"));

    MvcResult result = mockMvc.perform(
        post("/bucket/presigned-url")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
    ).andReturn();

    // Ensure the endpoint exists (not a 404). Specific status/behavior depends on controller implementation.
    assertNotEquals(404, result.getResponse().getStatus());
  }
}
