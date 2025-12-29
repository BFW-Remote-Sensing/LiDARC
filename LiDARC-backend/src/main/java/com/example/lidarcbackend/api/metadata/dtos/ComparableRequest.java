package com.example.lidarcbackend.api.metadata.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComparableRequest {
        @Min(value = 0, message = "Page number cannot be negative")
        private Integer page;

        @Min(value = 1, message = "Page size must be greater than zero")
        @Max(value = 100, message = "Page size cannot exceed 100")
        private Integer size;

}
