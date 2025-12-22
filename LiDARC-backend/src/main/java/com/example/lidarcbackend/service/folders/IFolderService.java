package com.example.lidarcbackend.service.folders;

import java.util.List;
import java.util.Map;
import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.model.DTO.CreateEmptyFolderDto;
import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.entity.Folder;

public interface IFolderService {
  FolderFilesDTO loadFolderWithFiles(Long folderId);

  Map<Long, FolderFilesDTO> loadFoldersWithFiles(List<Long> folderIds);

  Folder createFolder(CreateFolderDTO dto);

  EmptyFolderDto createFolderEmpty(CreateEmptyFolderDto emptyDto);
}
