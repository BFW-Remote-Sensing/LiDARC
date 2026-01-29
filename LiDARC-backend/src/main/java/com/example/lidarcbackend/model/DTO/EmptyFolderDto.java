package com.example.lidarcbackend.model.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class EmptyFolderDto {

  @NotBlank
  private String name;
  @NotNull
  private Long id;
  private String status = "UPLOADING";
}
