package com.example.lidarcbackend.model;

import java.util.List;

public record ValidationErrorRestDto(String message,
                                     List<String> errors) {
}
