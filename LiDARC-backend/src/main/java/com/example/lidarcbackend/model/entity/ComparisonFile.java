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

    @Column(name = "bucket")
    private String bucket;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "included")
    private Boolean included;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status {
        PREPROCESSING,
        COMPLETED,
        FAILED
    }

    //TODO: Add if processing?

    public ComparisonFile() {

    }
}

