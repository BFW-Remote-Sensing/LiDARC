package com.example.lidarcbackend.integration;

import com.example.lidarcbackend.base.TestMinioConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest
@Import(TestMinioConfiguration.class)
@ActiveProfiles("test")
public class BucketApiEndpointTests {

  @Autowired
  private MockMvc mockMvc;


  @Autowired
  private ObjectMapper objectMapper;

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
    assertNotEquals(404, putResult.getResponse().getStatus());
  }
}
