package com.example.lidarcbackend.service.folders;

import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.model.entity.Folder;

import java.util.List;
import java.util.Map;

public interface IFolderService {
    FolderFilesDTO loadFolderWithFiles(Long folderId);

    Map<Long, FolderFilesDTO> loadFoldersWithFiles(List<Long> folderIds);

    Folder createFolder(CreateFolderDTO dto);
}
