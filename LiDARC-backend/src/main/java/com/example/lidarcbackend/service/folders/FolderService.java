package com.example.lidarcbackend.service.folders;

import com.example.lidarcbackend.exception.NotFoundException;
import com.example.lidarcbackend.repository.ComparisonFolderRepository;
import com.example.lidarcbackend.service.files.MetadataService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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

@Slf4j
@Service
public class FolderService implements IFolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final MetadataMapper mapper;
    private final EmptyFolderMapper emptyFolderMapper;
    private final FolderMapper folderMapper;
    private final ComparisonFolderRepository comparisonFolderRepository;
    private final MetadataService metadataService;

    public FolderService(
            FolderRepository folderRepository,
            FileRepository fileRepository,
            MetadataMapper mapper,
            EmptyFolderMapper emptyFolderMapper,
            FolderMapper folderMapper,
            ComparisonFolderRepository comparisonFolderRepository,
            @Lazy MetadataService metadataService
    ) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.mapper = mapper;
        this.emptyFolderMapper = emptyFolderMapper;
        this.folderMapper = folderMapper;
        this.comparisonFolderRepository = comparisonFolderRepository;
        this.metadataService = metadataService;
    }

    public List<FolderDTO> getFolders() {


        return folderRepository.findAllActiveAndUncompared()
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
                folder.getActive(),
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
                            folder.getActive(),
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
        metadataService.checkFileAssignability(dto.getFileIds());

        // 2. Create folder
        Folder folder = Folder.builder()
                .name(dto.getName())
                .status(dto.getStatus())
                .active(true)
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
                .active(true)
                .build();
        folder = folderRepository.save(folder);
        return emptyFolderMapper.emptyFolderToDto(folder);
    }


    @Override
    public UploadedFolderDto folderUploaded(StatusOfUploadedFolderDto folderDto) {
        Folder folder = folderRepository.findById(folderDto.getId())
                .orElseThrow(() -> new BadRequestException("Folder with ID " + folderDto.getId() + " not found"));
        if (Objects.equals(folder.getStatus(), "UPLOADING")) {
            folder.setStatus(folderDto.getStatus());
            folder = folderRepository.save(folder);
        }
        return folderMapper.folderToDto(folder);
    }

    @Transactional
    public void deleteFolder(Long id) throws NotFoundException, BadRequestException {
        // Case 1: Check if the folder exists
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Folder with ID " + id + " not found"));

        // Case 2: Check if the folder is done with processing
        if (!folder.isFinalized()) {
            throw new BadRequestException("Cannot delete folder until it is done with processing.");
        }

        // Case 3: Check if the folder itself is in an ongoing comparison
        if (comparisonFolderRepository.isFolderInOngoingComparison(id)) {
            throw new BadRequestException("Cannot delete folder. It is currently being used in an ongoing comparison.");
        }

        // 4. Delete the files inside the folder.
        List<File> files = fileRepository.findAllByFolderId(id, Sort.unsorted());
        for (File file : files) {
            metadataService.deleteMetadataById(file.getId());
        }

        // 5. Check if used in COMPLETED/FAILED comparisons
        boolean usedInHistory = comparisonFolderRepository.existsByFolderId(id);

        if (usedInHistory) {
            // 6.1: Soft delete (Mark as active column as false)
            folder.setActive(false);
            folderRepository.save(folder);
            log.info("Folder id {} marked as inactive", id);
        } else {
            // 6.2: Hard delete from DB
            folderRepository.delete(folder);
            log.info("Folder id {} deleted completely from DB", id);
        }
    }

}

