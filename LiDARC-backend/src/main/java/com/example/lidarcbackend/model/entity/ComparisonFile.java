package com.example.lidarcbackend.model.entity;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "comparison_file")
@Getter
@Setter
@Builder
@AllArgsConstructor
@IdClass( ComparisonFilePK.class )
public class ComparisonFile {

    @Id
    @Column(name = "comparison_id")
    private Long comparisonId;

    @Id
    @Column(name = "file_id")
    private Long fileId;

    public ComparisonFile() {

    }
}

