package com.example.lidarcbackend.repository;

import com.example.lidarcbackend.model.CoordinateSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface CoordinateSystemRepository extends JpaRepository<CoordinateSystem, Long> {

    /**
     * Finds coordinate system by its authority and code
     *
     * @param authority the authority of the coordinate system (e.g. EPSG, ESRI, ...)
     * @param code the code or identifier of the coordinate system
     * @return Optional containing the matching CoordinateSystem entity if found,
     *  *         or an empty Optional if no matching entity exists
     */
    Optional<CoordinateSystem> findByAuthorityAndCode(String authority, String code);
}
