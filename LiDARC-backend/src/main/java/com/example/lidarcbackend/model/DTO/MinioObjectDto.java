package com.example.lidarcbackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MinioObjectDto {
    private String bucket;
    private String objectKey;
}
