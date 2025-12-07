package com.example.lidarcbackend.api.metadata.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class MetadataResponse {
    private List<FileMetadataDTO> items;
    private long totalItems;
    private int page;
    private int size;
}