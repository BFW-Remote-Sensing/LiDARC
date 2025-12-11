package com.example.lidarcbackend.base;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

public interface BucketBase {


  String BUCKET_NAME = "basebucket";

  static MinIOContainer getMinIOContainer(String containerName, String username, String password) {
    return new MinIOContainer(DockerImageName.parse(containerName).asCompatibleSubstituteFor("minio")).withUserName(username).withPassword(password);
  }

  static InputStream getTestFileStream() {
    //TODO make this into the actual file of resources
    String test = "test";
    return new ByteArrayInputStream(test.getBytes());
  }
}
