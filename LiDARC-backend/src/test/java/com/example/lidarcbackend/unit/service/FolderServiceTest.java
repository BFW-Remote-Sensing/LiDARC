package com.example.lidarcbackend.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.lidarcbackend.api.metadata.MetadataMapper;
import com.example.lidarcbackend.exception.BadRequestException;
import com.example.lidarcbackend.model.DTO.CreateEmptyFolderDto;
import com.example.lidarcbackend.model.DTO.EmptyFolderDto;
import com.example.lidarcbackend.model.DTO.Mapper.EmptyFolderMapper;
import com.example.lidarcbackend.model.DTO.Mapper.FolderMapper;
import com.example.lidarcbackend.model.DTO.StatusOfUploadedFolderDto;
import com.example.lidarcbackend.model.DTO.UploadedFolderDto;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import com.example.lidarcbackend.service.folders.FolderService;

@ExtendWith(MockitoExtension.class)
public class FolderServiceTest {

  @Mock
  private FolderRepository folderRepository;
  @Mock
  private FileRepository fileRepository;
  @Mock
  private MetadataMapper metadataMapper;
  @Mock
  private EmptyFolderMapper emptyFolderMapper;
  @Mock
  private FolderMapper folderMapper;

  @InjectMocks
  private FolderService folderService;

  @Test
  void createFolderEmpty_saves_and_maps() {
    CreateEmptyFolderDto input = new CreateEmptyFolderDto("name", "status");
    Folder saved = Folder.builder().id(10L).name("name").status("status").build();
    EmptyFolderDto dto = new EmptyFolderDto("name", 10L, "status");

    when(folderRepository.save(any(Folder.class))).thenReturn(saved);
    when(emptyFolderMapper.emptyFolderToDto(saved)).thenReturn(dto);

    EmptyFolderDto result = folderService.createFolderEmpty(input);

    assertThat(result).isEqualTo(dto);
    verify(folderRepository).save(any(Folder.class));
    verify(emptyFolderMapper).emptyFolderToDto(saved);
  }

  @Test
  void folderUploaded_returns_mapped_folder() {
    StatusOfUploadedFolderDto input = new StatusOfUploadedFolderDto(5L, "UPLOADED");
    Folder folder = Folder.builder().id(5L).name("f").status("s").build();
    UploadedFolderDto dto = new UploadedFolderDto("f", 5L, "s", List.of());

    when(folderRepository.findById(5L)).thenReturn(Optional.of(folder));
    when(folderMapper.folderToDto(folder)).thenReturn(dto);

    UploadedFolderDto result = folderService.folderUploaded(input);

    assertThat(result).isEqualTo(dto);
  }

  @Test
  void folderUploaded_throws_when_not_found() {
    StatusOfUploadedFolderDto input = new StatusOfUploadedFolderDto(99L, "UPLOADED");
    when(folderRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(BadRequestException.class, () -> folderService.folderUploaded(input));
  }
}
