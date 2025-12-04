package com.example.lidarcbackend.service;

import com.example.lidarcbackend.model.DTO.ImageInfoDto;
import jakarta.xml.bind.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class ImageService implements IImageService {

    private static final int MAX_FILE_SIZE_IN_BYTES = 5 * 1024 * 1024;
    private static final String UPLOAD_DIRECTORY = "src/main/resources/static/images"; //TODO: MAYBE CHANGE TO MINIO BUCKET IF WANTED

    @Override
    public ImageInfoDto save(InputStream file, long size, String originalFileName) throws IOException {
        if (size > MAX_FILE_SIZE_IN_BYTES) {
            throw new IllegalArgumentException("File size is larger than " + MAX_FILE_SIZE_IN_BYTES + " bytes");
        }
        String contentType = new Tika().detect(file);
        if (!contentType.startsWith("image")) {
            throw new IllegalArgumentException("File is not a image");
        }
        String fileExtension = FilenameUtils.getExtension(originalFileName);
        String uniqueFileName = generateUniqueFileName(fileExtension);

        Path uploadPath = Path.of(UPLOAD_DIRECTORY);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file, filePath, StandardCopyOption.REPLACE_EXISTING);
        return ImageInfoDto.builder().fileName(uniqueFileName).originalFileName(originalFileName).build();
    }

    private String generateUniqueFileName(String fileExtension) {
        String uniqueFileName;
        do {
            uniqueFileName = UUID.randomUUID() + "." + fileExtension;
        } while (Files.exists(Path.of(UPLOAD_DIRECTORY, uniqueFileName)));
        return uniqueFileName;
    }
}
