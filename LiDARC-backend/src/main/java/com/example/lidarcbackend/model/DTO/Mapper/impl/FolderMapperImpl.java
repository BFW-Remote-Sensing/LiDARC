package com.example.lidarcbackend.model.DTO.Mapper.impl;

import java.util.ArrayList;
import java.util.List;
import com.example.lidarcbackend.model.DTO.FileInfoDto;
import com.example.lidarcbackend.model.DTO.Mapper.FolderMapper;
import com.example.lidarcbackend.model.DTO.UploadedFolderDto;
import com.example.lidarcbackend.model.entity.Folder;

public class FolderMapperImpl implements FolderMapper {


  @Override
  public UploadedFolderDto folderToDto(Folder folder) {
    if (folder == null) {
      return null;
    }

    UploadedFolderDto uploadedFolderDto = new UploadedFolderDto();

    uploadedFolderDto.setId(folder.getId());
    uploadedFolderDto.setName(folder.getName());
    uploadedFolderDto.setStatus(folder.getStatus());
    List<FileInfoDto> files = new ArrayList<>();
    folder.getFiles().forEach(file -> {
      FileInfoDto fileInfoDto = new FileInfoDto(file);
      files.add(fileInfoDto);
    });
    uploadedFolderDto.setFiles(files);

    return uploadedFolderDto;


  }
}
