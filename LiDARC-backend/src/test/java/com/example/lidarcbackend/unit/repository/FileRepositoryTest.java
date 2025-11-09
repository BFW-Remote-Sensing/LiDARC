package com.example.lidarcbackend.unit.repository;

import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileRepositoryTest {





  @Autowired
  FileRepository fileRepository;

  private Url testUrl;

  private File testFileUploaded;

  private File testFileNotUploaded;

  @BeforeEach
  void setup() {



    testFileUploaded = new File();
    testFileUploaded.setFilename("testfile.txt");
    testFileUploaded.setUploaded(true);

    testFileNotUploaded = new File();
    testFileNotUploaded.setFilename("testfile_2.txt");
    testFileNotUploaded.setUploaded(true);


    fileRepository.save(testFileUploaded);
  }


  @Test
  void whenFindByUploadedIsTrue_thenReturnsUploadedFile() {
    var uploadedFiles = fileRepository.findFileByFilenameAndUploaded("testfile.txt", true);
    assert (uploadedFiles.isPresent());
    assert (uploadedFiles.get().getFilename().equals("testfile.txt"));
  }

  @Test
  void whenFindByUploadedIsFalse_thenReturnsUploadedFile() {
    var uploadedFiles = fileRepository.findFileByFilenameAndUploaded("testfile.txt", false);
    assert (uploadedFiles.isEmpty());
  }
}
