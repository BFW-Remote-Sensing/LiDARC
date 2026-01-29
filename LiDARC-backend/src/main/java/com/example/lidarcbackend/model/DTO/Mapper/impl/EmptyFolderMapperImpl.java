package com.example.lidarcbackend.model.DTO.Mapper.impl;

import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.DTO.Mapper.EmptyFolderMapper;
import com.example.lidarcbackend.model.entity.Folder;

public class EmptyFolderMapperImpl implements EmptyFolderMapper {
  @Override
  public EmptyFolderDto emptyFolderToDto(Folder folder) {
    EmptyFolderDto emptyFolderDto = new EmptyFolderDto();
    emptyFolderDto.setId(folder.getId());
    emptyFolderDto.setName(folder.getName());
    return emptyFolderDto;
  }
}
