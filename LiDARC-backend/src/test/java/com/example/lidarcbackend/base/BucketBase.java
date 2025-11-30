package com.example.lidarcbackend.base;

import org.testcontainers.containers.MinIOContainer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public interface BucketBase {

  public static MinIOContainer getMinIOContainer() {
    return new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z").withUserName("admin").withPassword("aseWS25LiDARC");
  };
  public static String BUCKET_NAME = "basebucket";

  public static InputStream getTestFileStream() {
    //TODO make this into the actual file of resources
    String test =  "test";
    return new ByteArrayInputStream(test.getBytes());
  }
}
