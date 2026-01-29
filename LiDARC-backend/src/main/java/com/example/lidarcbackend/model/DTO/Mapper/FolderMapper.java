package com.example.lidarcbackend.model.DTO.Mapper;

import org.mapstruct.Mapper;
import com.example.lidarcbackend.model.DTO.UploadedFolderDto;
import com.example.lidarcbackend.model.entity.Folder;

@Mapper(componentModel = "spring")
public interface FolderMapper {
  UploadedFolderDto folderToDto(Folder folder);
}
