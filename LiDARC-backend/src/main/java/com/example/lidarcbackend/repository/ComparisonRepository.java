package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComparisonRepository extends JpaRepository<Comparison, Long> {
    Optional<Comparison> findComparisonsById(Long id);

    Page<Comparison> findByNameContainingIgnoreCase(
            String originalFilename,
            Pageable pageable
    );
}
