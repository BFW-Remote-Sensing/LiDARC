package com.example.lidarcbackend.service;

import com.example.lidarcbackend.model.DTO.ImageInfoDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface IImageService {
    ImageInfoDto save(InputStream file, long size, String originalFileName) throws IOException;
}
