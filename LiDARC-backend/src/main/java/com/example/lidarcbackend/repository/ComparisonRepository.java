package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.entity.Comparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComparisonRepository extends JpaRepository<Comparison, Long> {
    Optional<Comparison> findComparisonsById(Long id);
}
