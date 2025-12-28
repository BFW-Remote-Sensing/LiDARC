package com.example.lidarcbackend.api.folder;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.model.DTO.CreateEmptyFolderDto;
import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.DTO.StatusOfUploadedFolderDto;
import com.example.lidarcbackend.model.DTO.UploadedFolderDto;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.service.folders.FolderService;

@RestController
@RequestMapping("/api/v1/folders")
@Slf4j
public class FolderController {
  private final FolderService folderService;

  @Autowired
  public FolderController(FolderService folderService) {
    this.folderService = folderService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<FolderFilesDTO> getComparison(@PathVariable Long id) {
    FolderFilesDTO dto = folderService.loadFolderWithFiles(id);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }

  @PostMapping
  public ResponseEntity<Folder> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
    Folder folder = folderService.createFolder(dto);
    return new ResponseEntity<>(folder, HttpStatus.CREATED);
  }

  @PostMapping("/empty")
  public ResponseEntity<EmptyFolderDto> createFolder(@Valid @RequestBody CreateEmptyFolderDto dto) {
    EmptyFolderDto folder = folderService.createFolderEmpty(dto);
    return new ResponseEntity<>(folder, HttpStatus.CREATED);
  }

  @PutMapping
  public ResponseEntity<UploadedFolderDto> folderUploaded(@Valid @RequestBody StatusOfUploadedFolderDto dto) {
    UploadedFolderDto folder = folderService.folderUploaded(dto);
    return new ResponseEntity<>(folder, HttpStatus.CREATED);
  }
}
