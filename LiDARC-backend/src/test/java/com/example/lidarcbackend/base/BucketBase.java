package com.example.lidarcbackend.base;

import org.testcontainers.containers.MinIOContainer;

public interface BucketBase {

  public static MinIOContainer getMinIOContainer() {
    return new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z").withUserName("admin").withPassword("aseWS25LiDARC");
  };

}
