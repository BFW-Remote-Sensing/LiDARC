package com.example.lidarcbackend.api.metadata.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class ComparableResponse {
    List<ComparableItemDTO> items;
    long totalItems;
}
