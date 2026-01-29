package com.example.lidarcbackend.service.comparisons;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for managing Server-Sent Events (SSE) connections for chunking results.
 * Emitters are keyed by both comparisonId and chunkSize to ensure clients receive
 * results only for the specific chunk size they requested.
 */
@Service
@Slf4j
public class ChunkingSseService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes
    private static final int DEFAULT_STREAM_CHUNK_SIZE = 64 * 1024; // 64KB chunks for streaming

    // Key is "comparisonId:chunkSize"
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ChunkingResultCacheService cacheService;
    private final ExecutorService streamingExecutor = Executors.newCachedThreadPool();
    private final int streamChunkSize;

    public ChunkingSseService(
            ChunkingResultCacheService cacheService,
            @Value("${app.sse.streaming.chunk-size-bytes:65536}") int streamChunkSize) {
        this.cacheService = cacheService;
        this.streamChunkSize = streamChunkSize > 0 ? streamChunkSize : DEFAULT_STREAM_CHUNK_SIZE;
    }

    /**
     * Creates and registers a new SSE emitter for a comparison with specific chunk size.
     * If result is already available in cache, streams it in chunks to handle large files.
     */
    public SseEmitter createEmitter(Long comparisonId, int chunkSize) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String key = buildKey(comparisonId, chunkSize);

        // Check if result is already in cache for this specific chunkSize
        cacheService.getRawJson(comparisonId, chunkSize).ifPresent(rawJson -> {
            // Stream the cached result asynchronously in chunks
            streamingExecutor.submit(() -> streamResultToEmitter(emitter, rawJson, comparisonId, chunkSize));
        });

        // Only register if result was not already sent
        if (cacheService.getRawJson(comparisonId, chunkSize).isEmpty()) {
            registerEmitter(key, emitter, comparisonId, chunkSize);
        }

        return emitter;
    }

    /**
     * Streams a large JSON result to an SSE emitter in chunks.
     * Sends a 'chunking-result-start' event, followed by multiple 'chunking-result-chunk' events,
     * and finally a 'chunking-result-end' event.
     */
    private void streamResultToEmitter(SseEmitter emitter, String rawJson, Long comparisonId, int chunkSize) {
        try {
            int totalLength = rawJson.length();
            int totalChunks = (int) Math.ceil((double) totalLength / streamChunkSize);

            log.info("Streaming cached result for comparisonId={}, chunkSize={}, totalSize={}KB, totalChunks={}",
                    comparisonId, chunkSize, totalLength / 1024, totalChunks);

            // Send start event with metadata
            emitter.send(SseEmitter.event()
                    .name("chunking-result-start")
                    .data("{\"totalChunks\":" + totalChunks + ",\"totalSize\":" + totalLength + "}"));

            // Stream data in chunks
            for (int i = 0; i < totalChunks; i++) {
                int start = i * streamChunkSize;
                int end = Math.min(start + streamChunkSize, totalLength);
                String chunk = rawJson.substring(start, end);

                emitter.send(SseEmitter.event()
                        .name("chunking-result-chunk")
                        .id(String.valueOf(i))
                        .data(chunk));
            }

            // Send end event
            emitter.send(SseEmitter.event()
                    .name("chunking-result-end")
                    .data("{\"status\":\"complete\"}"));

            emitter.complete();
            log.info("Successfully streamed cached result for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
        } catch (IOException e) {
            log.warn("Failed to stream cached result for comparisonId={}, chunkSize={}: {}", comparisonId, chunkSize, e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void registerEmitter(String key, SseEmitter emitter, Long comparisonId, int chunkSize) {
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError(e -> removeEmitter(key, emitter));

        log.info("Registered SSE emitter for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(key);
            }
        }
    }

    /**
     * Listens for ChunkingResultReadyEvent and broadcasts to subscribers
     * waiting for results with the matching chunkSize.
     * Streams the result from Redis cache in chunks to handle large files.
     */
    @EventListener
    public void handleChunkingResultReady(ChunkingResultReadyEvent event) {
        Long comparisonId = event.getComparisonId();
        int chunkSize = event.getChunkSize();
        String key = buildKey(comparisonId, chunkSize);

        List<SseEmitter> emitterList = emitters.get(key);
        if (emitterList == null || emitterList.isEmpty()) {
            log.info("No SSE subscribers for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
            return;
        }

        log.info("Broadcasting chunking result to {} subscribers for comparisonId={}, chunkSize={}",
                emitterList.size(), comparisonId, chunkSize);

        // Get raw JSON from cache for streaming
        cacheService.getRawJson(comparisonId, chunkSize).ifPresentOrElse(
                rawJson -> {
                    for (SseEmitter emitter : emitterList) {
                        // Stream each emitter asynchronously
                        streamingExecutor.submit(() -> streamResultToEmitter(emitter, rawJson, comparisonId, chunkSize));
                    }
                },
                () -> log.warn("No cached result found for comparisonId={}, chunkSize={}", comparisonId, chunkSize)
        );

        // Clear all emitters for this comparison/chunkSize after broadcast
        emitters.remove(key);
    }

    private String buildKey(Long comparisonId, int chunkSize) {
        return comparisonId + ":" + chunkSize;
    }
}

