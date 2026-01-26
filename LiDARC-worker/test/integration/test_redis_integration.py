import pytest
from redis_cache import get_redis_cache

@pytest.mark.e2e
def test_redis_cache_integration(redis_client):
    """
    Test that RedisCache can save and retrieve data from the actual Redis container.
    """
    cache = get_redis_cache()
    
    comparison_id = 123
    chunk_size = 10
    test_data = {
        "comparisonId": comparison_id,
        "chunkingSize": chunk_size,
        "status": "success",
        "values": [1.0, 2.0, 3.0]
    }
    
    # 1. Save data
    success = cache.save(comparison_id, chunk_size, test_data)
    assert success is True
    
    # 2. Check existence
    assert cache.exists(comparison_id, chunk_size) is True
    
    # 3. Retrieve data
    retrieved_data = cache.get(comparison_id, chunk_size)
    assert retrieved_data == test_data
    
    # 4. Delete data
    delete_success = cache.delete(comparison_id, chunk_size)
    assert delete_success is True
    assert cache.exists(comparison_id, chunk_size) is False

@pytest.mark.e2e
def test_redis_delete_all_integration(redis_client):
    """
    Test deleting all keys for a specific comparison ID.
    """
    cache = get_redis_cache()
    comparison_id = 456
    
    cache.save(comparison_id, 1, {"data": "size1"})
    cache.save(comparison_id, 2, {"data": "size2"})
    cache.save(comparison_id, 5, {"data": "size5"})
    
    assert cache.exists(comparison_id, 1) is True
    assert cache.exists(comparison_id, 2) is True
    assert cache.exists(comparison_id, 5) is True
    
    # Delete all for comparison_id
    cache.delete_all(comparison_id)
    
    assert cache.exists(comparison_id, 1) is False
    assert cache.exists(comparison_id, 2) is False
    assert cache.exists(comparison_id, 5) is False

@pytest.mark.e2e
def test_file_cache_evict_after_use(redis_client):
    """
    Test that FileRedisCache (via get_file_cache) can be evicted manually.
    """
    from util.file_handler import get_file_cache
    cache = get_file_cache()
    
    bucket = "test-bucket"
    key = "test-file"
    content = b"test content"
    
    cache.set(bucket, key, content)
    assert cache.get(bucket, key) == content
    
    # Manual delete
    cache.delete(bucket, key)
    assert cache.get(bucket, key) is None

@pytest.mark.e2e
def test_fetch_file_evicts_from_cache(redis_client, tmp_path):
    """
    Test that fetch_file deletes the entry from Redis after a cache hit.
    """
    from util.file_handler import get_file_cache, fetch_file
    cache = get_file_cache()
    
    bucket = "basebucket"
    key = "evict-me.las"
    content = b"fake las content"
    
    # Pre-populate cache
    cache.set(bucket, key, content)
    
    file_info = {"bucket": bucket, "objectKey": key}
    
    # First fetch - should be a cache hit and evict
    dest = tmp_path / "first"
    path1 = fetch_file(file_info, dest_dir=str(dest))
    with open(path1, "rb") as f:
        assert f.read() == content
        
    # Verify it was evicted from Redis
    assert cache.get(bucket, key) is None
