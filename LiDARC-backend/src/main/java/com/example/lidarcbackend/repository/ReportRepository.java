package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findTopByComparisonIdOrderByCreationDateDesc(Long comparisonId);

    List<Report> findByComparisonId(Long comparisonId, Pageable pageable);

    Page<Report> findByTitleContainingIgnoreCaseOrFileNameContainingIgnoreCase(String title, String fileName, Pageable pageable);

    Page<Report> findAll(Pageable pageable);
}
