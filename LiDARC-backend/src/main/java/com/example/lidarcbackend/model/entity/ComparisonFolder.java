package com.example.lidarcbackend.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "comparison_folder")
@Getter
@Setter
@Builder
@AllArgsConstructor
@IdClass( ComparisonFolderPK.class )
public class ComparisonFolder {

    @Id
    @Column(name = "comparison_id")
    private Long comparisonId;

    @Id
    @Column(name = "folder_id")
    private Long folderId;

    public ComparisonFolder() {

    }
}
