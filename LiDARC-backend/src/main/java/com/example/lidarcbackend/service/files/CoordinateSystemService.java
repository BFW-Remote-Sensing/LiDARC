package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.repository.CoordinateSystemRepository;
import org.springframework.stereotype.Service;

@Service
public class CoordinateSystemService implements ICoordinateSystemService {
    private final CoordinateSystemRepository coordinateSystemRepository;

    public CoordinateSystemService(CoordinateSystemRepository coordinateSystemRepository) {
        this.coordinateSystemRepository = coordinateSystemRepository;
    }

    public boolean existsWithId(Long id) {
        return coordinateSystemRepository.existsById(id);
    }
}