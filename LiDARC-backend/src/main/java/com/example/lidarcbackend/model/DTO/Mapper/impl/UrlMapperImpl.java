package com.example.lidarcbackend.model.DTO.Mapper.impl;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.UrlMapper;
import com.example.lidarcbackend.model.entity.Url;

public class UrlMapperImpl implements UrlMapper {
    @Override
    public FileInfoDto urlToFileInfoDto(Url url) {
        FileInfoDto fileInfoDto = new FileInfoDto(url.getFile().getFilename());
        fileInfoDto.setPresignedURL(url.getPresignedURL());
        fileInfoDto.setUrlExpiresAt(url.getExpiresAt());
        return fileInfoDto;
    }
}
