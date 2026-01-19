package com.example.lidarcbackend.service.comparisons;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing Server-Sent Events (SSE) connections for chunking results.
 * Emitters are keyed by both comparisonId and chunkSize to ensure clients receive
 * results only for the specific chunk size they requested.
 */
@Service
@Slf4j
public class ChunkingSseService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes

    // Key is "comparisonId:chunkSize"
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ChunkingResultCacheService cacheService;

    public ChunkingSseService(ChunkingResultCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Creates and registers a new SSE emitter for a comparison with specific chunk size.
     * If result is already available in cache, sends it immediately.
     */
    public SseEmitter createEmitter(Long comparisonId, int chunkSize) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String key = buildKey(comparisonId, chunkSize);

        // Check if result is already in cache for this specific chunkSize
        cacheService.get(comparisonId, chunkSize).ifPresent(result -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunking-result")
                        .data(result));
                emitter.complete();
                log.info("Sent cached result immediately for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
            } catch (IOException e) {
                log.warn("Failed to send cached result for comparisonId={}, chunkSize={}: {}", comparisonId, chunkSize, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        // Only register if result was not already sent
        if (cacheService.get(comparisonId, chunkSize).isEmpty()) {
            registerEmitter(key, emitter, comparisonId, chunkSize);
        }

        return emitter;
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
     */
    @EventListener
    public void handleChunkingResultReady(ChunkingResultReadyEvent event) {
        Long comparisonId = event.getComparisonId();
        int chunkSize = event.getChunkSize();
        Object result = event.getResult();
        String key = buildKey(comparisonId, chunkSize);

        List<SseEmitter> emitterList = emitters.get(key);
        if (emitterList == null || emitterList.isEmpty()) {
            log.info("No SSE subscribers for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
            return;
        }

        log.info("Broadcasting chunking result to {} subscribers for comparisonId={}, chunkSize={}",
                emitterList.size(), comparisonId, chunkSize);

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunking-result")
                        .data(result));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Failed to send SSE event for comparisonId={}, chunkSize={}: {}", comparisonId, chunkSize, e.getMessage());
                emitter.completeWithError(e);
            }
        }

        // Clear all emitters for this comparison/chunkSize after broadcast
        emitters.remove(key);
    }

    private String buildKey(Long comparisonId, int chunkSize) {
        return comparisonId + ":" + chunkSize;
    }
}

