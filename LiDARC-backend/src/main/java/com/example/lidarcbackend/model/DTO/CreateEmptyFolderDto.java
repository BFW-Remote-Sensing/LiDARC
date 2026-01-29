package com.example.lidarcbackend.model.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CreateEmptyFolderDto {
  @NotBlank
  private String name;
  @Builder.Default
  private String status = "UPLOADING";
}
