package com.example.lidarcbackend.service.folders;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.folder.dtos.FolderDTO;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.api.metadata.dtos.FileMetadataDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.exception.BadRequestException;
import com.example.lidarcbackend.model.DTO.CreateEmptyFolderDto;
import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.DTO.Mapper.EmptyFolderMapper;
import com.example.lidarcbackend.model.DTO.Mapper.FolderMapper;
import com.example.lidarcbackend.model.DTO.StatusOfUploadedFolderDto;
import com.example.lidarcbackend.model.DTO.UploadedFolderDto;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;

@Service
@RequiredArgsConstructor
public class FolderService implements IFolderService {

  private final FolderRepository folderRepository;
  private final FileRepository fileRepository;
  private final MetadataMapper mapper;
  private final EmptyFolderMapper emptyFolderMapper;
  private final FolderMapper folderMapper;

  public List<FolderDTO> getFolders() {
    return folderRepository.findAll()
        .stream()
        .map(folder -> new FolderDTO(folder.getId(), folder.getName()))
        .toList();
  }

  public FolderFilesDTO loadFolderWithFiles(Long folderId) {
    Folder folder = folderRepository.findById(folderId).orElseThrow();

    List<File> files =
        fileRepository.findAllByFolderId(
            folderId,
            Sort.by(Sort.Direction.DESC, "uploadedAt")
        );

    return new FolderFilesDTO(
        folder.getId(),
        folder.getName(),
        folder.getCreatedAt(),
        folder.getStatus(),
        files.stream().map(mapper::toDto).toList()
    );
  }

  public Map<Long, FolderFilesDTO> loadFoldersWithFiles(List<Long> folderIds) {

    if (folderIds.isEmpty()) {
      return Map.of();
    }

    List<Folder> folders = folderRepository.findAllById(folderIds);

    List<File> files =
        fileRepository.findAllByFolderIdIn(
            folderIds,
            Sort.by(Sort.Direction.DESC, "uploadedAt")
        );

    Map<Long, List<FileMetadataDTO>> filesByFolderId =
        files.stream()
            .collect(Collectors.groupingBy(
                f -> f.getFolder().getId(),
                Collectors.mapping(mapper::toDto, Collectors.toList())
            ));

    Map<Long, FolderFilesDTO> result = new HashMap<>();

    for (Folder folder : folders) {
      result.put(
          folder.getId(),
          new FolderFilesDTO(
              folder.getId(),
              folder.getName(),
              folder.getCreatedAt(),
              folder.getStatus(),
              filesByFolderId.getOrDefault(folder.getId(), List.of())
          )
      );
    }

    return result;
  }

  @Transactional
  public Folder createFolder(CreateFolderDTO dto) {
    // 1. Validate fileIds exist and are not already assigned
    List<File> files = fileRepository.findAllById(dto.getFileIds());

    if (files.size() != dto.getFileIds().size()) {
      throw new BadRequestException("One or more file IDs do not exist");
    }

    boolean anyAssigned = files.stream().anyMatch(f -> f.getFolder() != null);
    if (anyAssigned) {
      throw new BadRequestException("One or more files are already assigned to a folder");
    }

    // 2. Create folder
    Folder folder = Folder.builder()
        .name(dto.getName())
        .status(dto.getStatus())
        .build();

    folder = folderRepository.save(folder);

    // 3. Attach files
    for (File file : files) {
      file.setFolder(folder);
    }
    fileRepository.saveAll(files);

    return folder;
  }

  @Override
  public EmptyFolderDto createFolderEmpty(CreateEmptyFolderDto emptyDto) {
    Folder folder = Folder.builder()
        .name(emptyDto.getName())
        .status(emptyDto.getStatus())
        .build();
    folder = folderRepository.save(folder);
    return emptyFolderMapper.emptyFolderToDto(folder);
  }


  @Override
  public UploadedFolderDto folderUploaded(StatusOfUploadedFolderDto folderDto) {
    Folder folder = folderRepository.findById(folderDto.getId())
        .orElseThrow(() -> new BadRequestException("Folder with ID " + folderDto.getId() + " not found"));
    if(Objects.equals(folder.getStatus(), "UPLOADING")){
        folder.setStatus(folderDto.getStatus());
        folder = folderRepository.save(folder);
    }
    return folderMapper.folderToDto(folder);
  }

}

