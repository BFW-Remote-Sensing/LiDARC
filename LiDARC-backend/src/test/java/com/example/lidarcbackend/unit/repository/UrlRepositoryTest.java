package com.example.lidarcbackend.unit.repository;

import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Url;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class UrlRepositoryTest {

    @Autowired
    private UrlRepository urlRepository;

  @Autowired
  private FileRepository fileRepository;


  private Url testUrl;

  private File testFile;

  @BeforeEach
  void setUp() {
    testFile = new File();
    testFile.setFilename("testfile.txt");
    fileRepository.save(testFile);


    testUrl = new Url();
    testUrl.setPresignedURL("http://example.com");
    testUrl.setFile(testFile);
    urlRepository.save(testUrl);
  }

    @Test
    void testFindById() {
        Optional<Url> foundUrl = urlRepository.findById(testUrl.getId());
        assertThat(foundUrl).isPresent();
        assertThat(foundUrl.get().getPresignedURL()).isEqualTo("http://example.com");
    }

    @Test
    void testSaveUrl() {
        Url newUrl = new Url();
        newUrl.setPresignedURL("http://newurl.com");
        newUrl.setFile(testFile);
        Url savedUrl = urlRepository.save(newUrl);

        assertThat(savedUrl.getId()).isNotNull();
        assertThat(savedUrl.getPresignedURL()).isEqualTo("http://newurl.com");
    }

    @Test
    void testDeleteUrl() {
        urlRepository.delete(testUrl);
        Optional<Url> deletedUrl = urlRepository.findById(testUrl.getId());
        assertThat(deletedUrl).isNotPresent();
    }
}
