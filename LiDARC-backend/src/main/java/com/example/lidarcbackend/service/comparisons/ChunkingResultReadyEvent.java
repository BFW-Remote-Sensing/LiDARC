package com.example.lidarcbackend.service.comparisons;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when chunking results are ready for a comparison.
 */
public class ChunkingResultReadyEvent extends ApplicationEvent {

    private final Long comparisonId;
    private final Integer chunkSize;
    private final Object result;

    public ChunkingResultReadyEvent(Object source, Long comparisonId, Integer chunkSize, Object result) {
        super(source);
        this.comparisonId = comparisonId;
        this.result = result;
        this.chunkSize = chunkSize;
    }

    public Long getComparisonId() {
        return comparisonId;
    }

    public Object getResult() {
        return result;
    }
    public Integer getChunkSize() {
        return chunkSize;
    }
}

