package com.example.lidarcbackend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    private String title;
    @JoinColumn(name = "comparison_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Comparison comparison;
    @Column(name = "creation_date", updatable = false)
    @CreationTimestamp
    private Instant creationDate;
}
