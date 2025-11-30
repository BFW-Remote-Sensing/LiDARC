package com.example.lidarcbackend.model.DTO.Mapper;

import org.mapstruct.Mapper;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.entity.Url;

@Mapper(componentModel = "spring")
public interface UrlMapper {
  FileInfoDto urlToFileInfoDto(Url url);
}
