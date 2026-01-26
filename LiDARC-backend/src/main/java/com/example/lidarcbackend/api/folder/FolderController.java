package com.example.lidarcbackend.api.folder;

import com.example.lidarcbackend.exception.BadRequestException;
import com.example.lidarcbackend.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.folder.dtos.FolderDTO;
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
  public ResponseEntity<FolderFilesDTO> getFolder(@PathVariable Long id) {
    FolderFilesDTO dto = folderService.loadFolderWithFiles(id);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }

  @GetMapping("/actives-without-comparison")
  public ResponseEntity<List<FolderDTO>> getFolders() {
    List<FolderDTO> dto = folderService.getFolders();

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

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteFolder(@PathVariable Long id) {
      try {
          folderService.deleteFolder(id);
          return ResponseEntity.noContent().build();
      } catch (NotFoundException e) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
      } catch (BadRequestException e) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
      }
  }
}
