package com.example.lidarcbackend.model.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "comparisons")
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Comparison {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "need_highest_vegetation")
    private Boolean needHighestVegetation;

    @Column(name = "need_outlier_detection")
    private Boolean needOutlierDetection;

    @Column(name = "need_statistics_over_scenery")
    private Boolean needStatisticsOverScenery;

    @Column(name = "need_most_differences")
    private Boolean needMostDifferences;

    @Column(name = "individual_statistics_percentile")
    private Double individualStatisticsPercentile;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    //@Column(name = "result_report_url")
    //private String resultReportUrl;

    @Column(name = "result_bucket")
    private String resultBucket;

    @Column(name = "result_object_key")
    private String resultObjectKey;

    @OneToMany(mappedBy = "comparison")
    private List<Report> reports;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "grid_cell_width")
    private Integer gridCellWidth;

    @Column(name = "grid_cell_height")
    private Integer gridCellHeight;

    @Column(name = "grid_min_x")
    private Double gridMinX;

    @Column(name = "grid_max_x")
    private Double gridMaxX;

    @Column(name = "grid_min_y")
    private Double gridMinY;

    @Column(name = "grid_max_y")
    private Double gridMaxY;

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }

    public Comparison() {

    }
}
