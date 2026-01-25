"""
Redis cache client for storing chunking results.
"""
import json
import logging
import os
from typing import Any, Optional

import redis

# Redis configuration from environment variables
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_TTL_MINUTES = int(os.getenv("REDIS_CHUNKING_TTL_MINUTES", "2"))

# Cache key prefix (must match backend)
CACHE_KEY_PREFIX = "chunking:result:"


class RedisCache:
    """Redis cache client for chunking results."""

    def __init__(self):
        self._client: Optional[redis.Redis] = None

    def _get_client(self) -> redis.Redis:
        """Get or create Redis client with lazy initialization."""
        if self._client is None:
            self._client = redis.Redis(
                host=REDIS_HOST,
                port=REDIS_PORT,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=10
            )
        return self._client

    def save(self, comparison_id: int, chunk_size: int, result: Any) -> bool:
        """
        Save chunking result to Redis with TTL.
        Key includes chunk_size to differentiate results with different chunk sizes.

        Args:
            comparison_id: The comparison ID
            chunk_size: The chunk size used for this result
            result: The result object to cache (will be JSON serialized)

        Returns:
            True if save was successful, False otherwise
        """
        key = f"{CACHE_KEY_PREFIX}{comparison_id}:{chunk_size}"
        try:
            client = self._get_client()
            # Serialize result to JSON
            json_result = json.dumps(result)
            # Set with expiration (TTL in seconds)
            ttl_seconds = REDIS_TTL_MINUTES * 60
            client.setex(key, ttl_seconds, json_result)
            logging.info(f"Saved chunking result to Redis for comparisonId={comparison_id}, chunkSize={chunk_size}, TTL={REDIS_TTL_MINUTES}min")
            return True
        except redis.RedisError as e:
            logging.error(f"Failed to save chunking result to Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return False
        except Exception as e:
            logging.error(f"Unexpected error saving to Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return False

    def get(self, comparison_id: int, chunk_size: int) -> Optional[Any]:
        """
        Get chunking result from Redis for a specific chunk size.

        Args:
            comparison_id: The comparison ID
            chunk_size: The chunk size to retrieve

        Returns:
            The cached result or None if not found/error
        """
        key = f"{CACHE_KEY_PREFIX}{comparison_id}:{chunk_size}"
        try:
            client = self._get_client()
            json_result = client.get(key)
            if json_result:
                return json.loads(json_result)
            return None
        except redis.RedisError as e:
            logging.error(f"Failed to get chunking result from Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return None
        except Exception as e:
            logging.error(f"Unexpected error getting from Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return None

    def delete(self, comparison_id: int, chunk_size: int) -> bool:
        """
        Delete chunking result from Redis for a specific chunk size.

        Args:
            comparison_id: The comparison ID
            chunk_size: The chunk size to delete

        Returns:
            True if delete was successful, False otherwise
        """
        key = f"{CACHE_KEY_PREFIX}{comparison_id}:{chunk_size}"
        try:
            client = self._get_client()
            client.delete(key)
            logging.info(f"Deleted chunking result from Redis for comparisonId={comparison_id}, chunkSize={chunk_size}")
            return True
        except redis.RedisError as e:
            logging.error(f"Failed to delete chunking result from Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return False

    def delete_all(self, comparison_id: int) -> bool:
        """
        Delete all chunking results for a comparison (all chunk sizes).

        Args:
            comparison_id: The comparison ID

        Returns:
            True if delete was successful, False otherwise
        """
        pattern = f"{CACHE_KEY_PREFIX}{comparison_id}:*"
        try:
            client = self._get_client()
            keys = client.keys(pattern)
            if keys:
                client.delete(*keys)
                logging.info(f"Deleted all chunking results from Redis for comparisonId={comparison_id}, count={len(keys)}")
            return True
        except redis.RedisError as e:
            logging.error(f"Failed to delete all chunking results from Redis for comparisonId={comparison_id}: {e}")
            return False

    def exists(self, comparison_id: int, chunk_size: int) -> bool:
        """
        Check if chunking result exists in Redis for a specific chunk size.

        Args:
            comparison_id: The comparison ID
            chunk_size: The chunk size to check

        Returns:
            True if exists, False otherwise
        """
        key = f"{CACHE_KEY_PREFIX}{comparison_id}:{chunk_size}"
        try:
            client = self._get_client()
            return bool(client.exists(key))
        except redis.RedisError as e:
            logging.error(f"Failed to check existence in Redis for comparisonId={comparison_id}, chunkSize={chunk_size}: {e}")
            return False


# Singleton instance
_cache_instance: Optional[RedisCache] = None


def get_redis_cache() -> RedisCache:
    """Get the singleton Redis cache instance."""
    global _cache_instance
    if _cache_instance is None:
        _cache_instance = RedisCache()
    return _cache_instance

