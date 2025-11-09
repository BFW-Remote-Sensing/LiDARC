package com.example.lidarcbackend.model.DTO.Mapper;

import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.entity.Url;
import org.mapstruct.Mapper;

@Mapper
public interface UrlMapper {
  FileInfoDto urlToFileInfoDto(Url url);
}
