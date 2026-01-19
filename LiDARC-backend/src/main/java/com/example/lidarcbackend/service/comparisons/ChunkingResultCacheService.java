package com.example.lidarcbackend.service.comparisons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for caching chunking results in Redis.
 * Uses StringRedisTemplate for cross-language compatibility with Python worker.
 * Cache key includes chunk size to ensure results match the requested chunking.
 */
@Service
@Slf4j
public class ChunkingResultCacheService {

    private static final String CACHE_KEY_PREFIX = "chunking:result:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;

    public ChunkingResultCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redis.chunking.ttl-minutes:30}") long ttlMinutes) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Save chunking result to Redis with TTL.
     * Key includes chunkSize to differentiate results with different chunk sizes.
     */
    public void save(Long comparisonId, int chunkSize, Object result) {
        String key = buildKey(comparisonId, chunkSize);
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(key, jsonResult, ttlMinutes, TimeUnit.MINUTES);
            log.info("Saved chunking result to Redis for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chunking result for comparisonId={}: {}", comparisonId, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save chunking result to Redis for comparisonId={}: {}", comparisonId, e.getMessage());
        }
    }

    /**
     * Get chunking result from Redis for a specific chunk size.
     * Handles results written by both Java backend and Python worker.
     */
    public Optional<Object> get(Long comparisonId, int chunkSize) {
        String key = buildKey(comparisonId, chunkSize);
        try {
            String jsonResult = stringRedisTemplate.opsForValue().get(key);
            if (jsonResult != null) {
                Object result = objectMapper.readValue(jsonResult, Object.class);
                return Optional.of(result);
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize chunking result from Redis for comparisonId={}, chunkSize={}: {}",
                    comparisonId, chunkSize, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get chunking result from Redis for comparisonId={}, chunkSize={}: {}",
                    comparisonId, chunkSize, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delete chunking result from Redis for a specific chunk size.
     */
    public void delete(Long comparisonId, int chunkSize) {
        String key = buildKey(comparisonId, chunkSize);
        try {
            stringRedisTemplate.delete(key);
            log.info("Deleted chunking result from Redis for comparisonId={}, chunkSize={}", comparisonId, chunkSize);
        } catch (Exception e) {
            log.error("Failed to delete chunking result from Redis for comparisonId={}, chunkSize={}: {}",
                    comparisonId, chunkSize, e.getMessage());
        }
    }

    /**
     * Delete all chunking results for a comparison (all chunk sizes).
     */
    public void deleteAll(Long comparisonId) {
        String pattern = CACHE_KEY_PREFIX + comparisonId + ":*";
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("Deleted all chunking results from Redis for comparisonId={}, count={}", comparisonId, keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to delete all chunking results from Redis for comparisonId={}: {}", comparisonId, e.getMessage());
        }
    }

    /**
     * Check if a chunking result exists in Redis for a specific chunk size.
     */
    public boolean exists(Long comparisonId, int chunkSize) {
        String key = buildKey(comparisonId, chunkSize);
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Failed to check existence in Redis for comparisonId={}, chunkSize={}: {}",
                    comparisonId, chunkSize, e.getMessage());
            return false;
        }
    }

    private String buildKey(Long comparisonId, int chunkSize) {
        return CACHE_KEY_PREFIX + comparisonId + ":" + chunkSize;
    }
}

