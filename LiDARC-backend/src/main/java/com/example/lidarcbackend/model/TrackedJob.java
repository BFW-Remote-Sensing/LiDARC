package com.example.lidarcbackend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TrackedJob {
    private final UUID jobId;
    private final JobType jobType;
    private final Map<String, Long> payload;
    private final Instant  startTime;
    private final Duration timeout;

    public boolean isExpired(Instant now) {
        return startTime.plus(timeout).isBefore(now);
    }

}
