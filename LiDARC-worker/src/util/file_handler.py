import json
import os
import logging
from io import BytesIO
from typing import Optional

import redis
import requests
from minio import Minio
from minio.error import S3Error
from urllib.parse import urlparse
from requests.adapters import HTTPAdapter, Retry

BUCKET_NAME = os.environ.get("BUCKET_NAME", "basebucket")
BASE_URL = os.environ.get("BASE_URL", "http://localhost:9000/")

# Redis file cache configuration from environment variables
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
FILE_CACHE_MAX_COUNT = int(os.getenv("FILE_CACHE_MAX_COUNT", "10"))
FILE_CACHE_MAX_SIZE_MB = int(os.getenv("FILE_CACHE_MAX_SIZE_MB", "20"))
FILE_CACHE_TTL_SECONDS = int(os.getenv("FILE_CACHE_TTL_SECONDS", "30"))
FILE_CACHE_KEY_PREFIX = "file:cache:"


class FileRedisCache:
    """Redis cache for caching small files fetched from MinIO."""

    def __init__(self):
        self._client: Optional[redis.Redis] = None

    def _get_client(self) -> redis.Redis:
        """Get or create Redis client with lazy initialization."""
        if self._client is None:
            self._client = redis.Redis(
                host=REDIS_HOST,
                port=REDIS_PORT,
                decode_responses=False,  # We need binary for file data
                socket_connect_timeout=5,
                socket_timeout=10
            )
        return self._client

    def _get_cache_key(self, bucket: str, object_key: str) -> str:
        """Generate cache key for a file."""
        return f"{FILE_CACHE_KEY_PREFIX}{bucket}:{object_key}"

    def _get_current_cache_count(self) -> int:
        """Get the current number of cached files."""
        try:
            client = self._get_client()
            keys = client.keys(f"{FILE_CACHE_KEY_PREFIX}*")
            return len(keys)
        except redis.RedisError as e:
            logging.warning(f"Failed to get cache count from Redis: {e}")
            return 0

    def _evict_oldest_if_needed(self):
        """Evict oldest cache entries if we're at max capacity."""
        try:
            client = self._get_client()
            keys = client.keys(f"{FILE_CACHE_KEY_PREFIX}*")
            if len(keys) >= FILE_CACHE_MAX_COUNT:
                # Find the key with the lowest TTL (closest to expiring)
                oldest_key = None
                lowest_ttl = float('inf')
                for key in keys:
                    ttl = client.ttl(key)
                    if ttl < lowest_ttl:
                        lowest_ttl = ttl
                        oldest_key = key
                if oldest_key:
                    client.delete(oldest_key)
                    logging.debug(f"Evicted oldest cache entry: {oldest_key}")
        except redis.RedisError as e:
            logging.warning(f"Failed to evict oldest cache entry: {e}")

    def get(self, bucket: str, object_key: str) -> Optional[bytes]:
        """
        Get file content from Redis cache.

        Args:
            bucket: The bucket name
            object_key: The object key

        Returns:
            The cached file content as bytes or None if not found
        """
        key = self._get_cache_key(bucket, object_key)
        try:
            client = self._get_client()
            data = client.get(key)
            if data:
                logging.info(f"Cache hit for {bucket}/{object_key}")
                return data
            logging.debug(f"Cache miss for {bucket}/{object_key}")
            return None
        except redis.RedisError as e:
            logging.warning(f"Failed to get file from Redis cache: {e}")
            return None

    def set(self, bucket: str, object_key: str, content: bytes) -> bool:
        """
        Cache file content in Redis if it's under the size limit.

        Args:
            bucket: The bucket name
            object_key: The object key
            content: The file content as bytes

        Returns:
            True if caching was successful, False otherwise
        """
        # Check size limit
        size_mb = len(content) / (1024 * 1024)
        if size_mb > FILE_CACHE_MAX_SIZE_MB:
            logging.debug(f"File {bucket}/{object_key} too large to cache ({size_mb:.2f}MB > {FILE_CACHE_MAX_SIZE_MB}MB)")
            return False

        key = self._get_cache_key(bucket, object_key)
        try:
            # Evict oldest if at capacity
            self._evict_oldest_if_needed()

            client = self._get_client()
            client.setex(key, FILE_CACHE_TTL_SECONDS, content)
            logging.info(f"Cached file {bucket}/{object_key} ({size_mb:.2f}MB) with TTL={FILE_CACHE_TTL_SECONDS}s")
            return True
        except redis.RedisError as e:
            logging.warning(f"Failed to cache file in Redis: {e}")
            return False


# Singleton instance for file cache
_file_cache_instance: Optional[FileRedisCache] = None


def get_file_cache() -> FileRedisCache:
    """Get the singleton file cache instance."""
    global _file_cache_instance
    if _file_cache_instance is None:
        _file_cache_instance = FileRedisCache()
    return _file_cache_instance


def minio_client():
    endpoint_url = os.environ.get("MINIO_ENDPOINT", "minio:9000")
    access_key = os.environ.get("MINIO_ACCESS_KEY", "admin")
    secret_key = os.environ.get("MINIO_SECRET_KEY", "aseWS25LiDARC")
    secure = os.environ.get("MINIO_SECURE", False)

    return Minio(
        endpoint=endpoint_url,
        access_key=access_key,
        secret_key=secret_key,
        secure=secure,
    )

def upload_file(source_file):
    client = minio_client()
    destination_file = source_file.split("/")[-1]
    found = client.bucket_exists(BUCKET_NAME)
    if not found:
        logging.warning(f"Bucket {BUCKET_NAME} does not exist")
    else:
        logging.info(f"Bucket {BUCKET_NAME} exists, proceeding to upload!")

    client.fput_object(
        BUCKET_NAME, destination_file, source_file
    )
    logging.info(f"Uploaded {source_file} to {BUCKET_NAME} as {destination_file}")

    # Cache the file after upload
    try:
        with open(source_file, 'rb') as f:
            content = f.read()
        get_file_cache().set(BUCKET_NAME, destination_file, content)
    except Exception as cache_err:
        logging.warning(f"Failed to cache file after upload: {cache_err}")

def upload_file_by_type(destination_file, data):
    ext = os.path.splitext(destination_file)[1].lower()

    handlers = {
        ".csv": lambda: upload_df_as_csv(destination_file, data),
        ".json": lambda: (
            upload_df_as_json(destination_file, data)
            if hasattr(data, "to_json")
            else upload_json(destination_file, data)
        )
    }

    if ext not in handlers:
        logging.warning(f"File type {ext} is not supported")

    return handlers[ext]()

def upload_csv(destination_file, data_buf, length):
    client = minio_client()
    client.put_object(BUCKET_NAME,
                      destination_file,
                      data_buf,
                      length=length,
                      content_type="application/csv")
    
    # Cache the file after upload
    try:
        data_buf.seek(0)
        content = data_buf.read()
        get_file_cache().set(BUCKET_NAME, destination_file, content)
        data_buf.seek(0) # Reset buffer for any subsequent use
    except Exception as cache_err:
        logging.warning(f"Failed to cache csv after upload: {cache_err}")

    return {
        "bucket": BUCKET_NAME,
        "objectKey": destination_file,
    }

def upload_df_as_csv(destination_file, df):
    csv_bytes = df.to_csv().encode('utf-8')
    csv_buffer = BytesIO(csv_bytes)
    return upload_csv(destination_file, csv_buffer, len(csv_bytes))

def upload_json(destination_file, json_obj):
    client = minio_client()
    if isinstance(json_obj, str):
        json_bytes = json_obj.encode('utf-8')
    else:
        json_bytes = json.dumps(json_obj).encode('utf-8')

    json_buffer = BytesIO(json_bytes)
    client.put_object(
        BUCKET_NAME,
        destination_file,
        json_buffer,
        length=len(json_bytes),
        content_type="application/json"
    )

    # Cache the file after upload
    try:
        get_file_cache().set(BUCKET_NAME, destination_file, json_bytes)
    except Exception as cache_err:
        logging.warning(f"Failed to cache json after upload: {cache_err}")

    return {
        "bucket": BUCKET_NAME,
        "objectKey": destination_file
    }

def upload_df_as_json(destination_file, df):
    client = minio_client()
    json_bytes = df.to_json(orient="records").encode('utf-8')
    json_buffer = BytesIO(json_bytes)

    client.put_object(
        BUCKET_NAME,
        destination_file,
        json_buffer,
        length=len(json_bytes),
        content_type="application/json"
    )

    # Cache the file after upload
    try:
        get_file_cache().set(BUCKET_NAME, destination_file, json_bytes)
    except Exception as cache_err:
        logging.warning(f"Failed to cache dataframe json after upload: {cache_err}")

    return BASE_URL + destination_file

def download_file(url: str, dest_dir: str = ".", chunk_size: int = 10* 1024 ) -> str:
    os.makedirs(dest_dir, exist_ok=True)
    parsed = urlparse(url)
    local_filename = os.path.join(dest_dir, os.path.basename(parsed.path))

    session = requests.Session()
    retries = Retry(
        total=5,
        backoff_factor=1,
        status_forcelist=[500, 502, 503, 504],
        allowed_methods=["GET"],
    )
    session.mount("https://", HTTPAdapter(max_retries=retries))
    session.mount("http://", HTTPAdapter(max_retries=retries))
    try:
        with requests.get(url, stream=True) as r:
            r.raise_for_status()
            with open(local_filename, 'wb') as f:
                for chunk in r.iter_content(chunk_size=chunk_size):
                    f.write(chunk)
        return local_filename
    except Exception as e:
        if os.path.exists(local_filename):
            os.remove(local_filename)
        raise RuntimeError(f"Download failed for {url}: {e}") from e

def fetch_file(file, dest_dir: str=".") -> str:
    os.makedirs(dest_dir, exist_ok=True)
    bucket_name = file.get("bucket")
    object_key = file.get("objectKey")

    if not bucket_name or not object_key:
        raise ValueError("File object must contain 'bucket' and 'objectKey'")
    local_filename = os.path.join(dest_dir, os.path.basename(object_key))

    # Try to get from Redis cache first
    file_cache = get_file_cache()
    cached_content = file_cache.get(bucket_name, object_key)
    if cached_content is not None:
        with open(local_filename, 'wb') as f:
            f.write(cached_content)
        return local_filename

    # Fetch from MinIO
    client = minio_client()
    try:
        client.fget_object(
            bucket_name=bucket_name,
            object_name=object_key,
            file_path=local_filename
        )

        # Cache the file if it's small enough
        try:
            with open(local_filename, 'rb') as f:
                content = f.read()
            file_cache.set(bucket_name, object_key, content)
        except Exception as cache_err:
            logging.warning(f"Failed to cache file after download: {cache_err}")

        return local_filename
    except Exception as e:
        if os.path.exists(local_filename):
            os.remove(local_filename)
        raise RuntimeError(f"MinIO download failed for {bucket_name}/{object_key}: {e}") from e

def main():
    test_file = os.path.join(os.getcwd(), "../Pre_Process_Job_0001_output.csv")
    try:
        upload_file(test_file)
    except S3Error as e:
        logging.error("Error occurred while uploading file to Minio:  {}".format(e))


if __name__ == "__main__":
    try:
        main()
    except S3Error as e:
        print("error occurred: ", e)
