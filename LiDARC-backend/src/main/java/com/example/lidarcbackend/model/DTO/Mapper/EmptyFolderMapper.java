package com.example.lidarcbackend.model.DTO.Mapper;

import org.mapstruct.Mapper;
import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.entity.Folder;

@Mapper(componentModel = "spring")
public interface EmptyFolderMapper {
  EmptyFolderDto emptyFolderToDto(Folder folder);
}
