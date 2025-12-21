package com.example.lidarcbackend.api.folder;

import com.example.lidarcbackend.api.folder.dtos.CreateFolderDTO;
import com.example.lidarcbackend.api.metadata.dtos.FolderFilesDTO;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.service.folders.FolderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/folders")
@Slf4j
public class FolderController {
    private final FolderService folderService;

    @Autowired
    public FolderController(FolderService folderService){
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
}
