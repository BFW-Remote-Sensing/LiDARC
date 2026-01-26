package com.example.lidarcbackend.service;

import com.example.lidarcbackend.model.TrackedJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IJobTrackingService {
    void registerJob(TrackedJob job);

    void completeJob(UUID jobId);


    Optional<TrackedJob> getJob(UUID jobId);

}
