package com.example.lidarcbackend.api.metadata.dtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetadataRequest {

    @Min(value = 0, message = "Page number cannot be negative")
    private Integer page;

    @Min(value = 1, message = "Page size must be greater than zero")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private Integer size;

    @NotBlank(message = "sortBy cannot be blank")
    @Pattern(
            regexp = "id|filename|uploadedAt|sizeBytes|uploaded",
            message = "Invalid sortBy field. Allowed values: id, filename, uploadedAt, sizeBytes, uploaded"
    )
    private String sortBy = "uploadedAt";

    private boolean ascending = false;
}