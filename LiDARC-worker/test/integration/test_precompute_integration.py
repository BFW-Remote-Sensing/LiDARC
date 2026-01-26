import json
import threading
from http import HTTPMethod

import pika
import pytest
import pytest_check as check
import pandas as pd
import preprocess.preprocess_worker as preprocess_worker
from messaging.rabbit_config import get_rabbitmq_config

rabbitConfig = get_rabbitmq_config()
WORKER_EXCHANGE = rabbitConfig.exchange_worker_job
PREPROCESSING_JOB_QUEUE = rabbitConfig.queue_preprocessing_job
PREPROCESSING_RESULT_QUEUE = rabbitConfig.queue_preprocessing_result
PREPROCESSING_JOB_RK = rabbitConfig.routing_preprocessing_start

def publish_message(channel, exchange, routing_key, message_dict):
    channel.basic_publish(
        exchange=exchange,
        routing_key=routing_key,
        body=json.dumps(message_dict),
        properties=pika.BasicProperties(content_type="application/json"),
    )

def consume_single_message(channel, queue):
    method_frame, header_frame, body = None, None, ""

    def callback(ch, method, properties, body_msg):
        nonlocal method_frame, header_frame, body
        method_frame, header_frame, body = method, properties, body_msg
        ch.basic_ack(delivery_tag=method.delivery_tag)
        channel.stop_consuming()

    channel.basic_consume(queue=queue, on_message_callback=callback)
    channel.start_consuming()
    return body

@pytest.mark.e2e
def test_precompute_integration(minio_client, rabbitmq_ch, load_json, very_small_las_file):
    client, upload_file = minio_client
    assert client.bucket_exists("basebucket")
    upload_file(very_small_las_file, object_name="small.las")

    def run_worker():
        try:
            preprocess_worker.main()
        except SystemExit:
            pass
    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_msg = load_json("valid_precompute_job_small_las_file.json")
    test_msg["file"] = {
        "bucket": "basebucket",
        "objectKey": "small.las"
    }
    publish_message(rabbitmq_ch, WORKER_EXCHANGE, PREPROCESSING_JOB_RK, test_msg)
    body = consume_single_message(rabbitmq_ch, PREPROCESSING_RESULT_QUEUE)
    assert body is not None, "Result message of preprocess worker is None"
    response = json.loads(body)

    check.equal(response["status"], "success", "Status is not success")
    check.equal(response["job_id"], "12345", "JobId is")
    payload = response["payload"]
    summary = payload["summary"]
    check.equal(summary["nCells"], 100, "Number of cells is not 100 cells")
    check.equal(summary["maxZ"], 20.0, "Highest Z value is not 20.0")
    check.equal(summary["minZ"], 5.0, "Lowest Z value is not 5.0")
    result = payload["result"]
    check.equal(result["bucket"], "basebucket", "Bucket is not basebucket")
    csv_obj = client.get_object(bucket_name="basebucket", object_name=result['objectKey'])
    df = pd.read_csv(csv_obj)
    expected_points = {
        (0.0, 0.0): 2,
        (2.0, 2.0): 1,
        (9.0, 9.0): 1,
    }
    for (expected_x0, expected_y0), expected_count in expected_points.items():
        filtered_df = df[(df["x0"] == expected_x0)& (df["y0"] == expected_y0)]

        check.equal(len(filtered_df), 1)
        actual_count = filtered_df["count"].iloc[0]
        check.equal(actual_count, expected_count, f"Count mismatch for cell ({expected_x0}, {expected_y0}): expected {expected_count}, got {actual_count}")


@pytest.mark.e2e
def test_precompute_integration_with_small_las_file(minio_client, rabbitmq_ch, load_json, small_las_file):
    client, upload_file = minio_client
    assert client.bucket_exists("basebucket")
    upload_file(small_las_file, object_name="small.las")

    def run_worker():
        try:
            preprocess_worker.main()
        except SystemExit:
            pass
    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_msg = load_json("valid_precompute_job_small_las_file.json")
    test_msg["file"] = {
        "bucket": "basebucket",
        "objectKey": "small.las"
    }
    publish_message(rabbitmq_ch, WORKER_EXCHANGE, PREPROCESSING_JOB_RK, test_msg)
    body = consume_single_message(rabbitmq_ch, PREPROCESSING_RESULT_QUEUE)
    assert body is not None, "Result message of preprocess worker is None"
    response = json.loads(body)

    check.equal(response["status"], "success", "Status is not success")
    check.equal(response["job_id"], "12345", "JobId is")

    payload = response["payload"]

    summary = payload["summary"]
    check.equal(summary["nCells"], 100, "Number of cells is not 100 cells")
    check.equal(summary["maxZ"], 20.0, "Highest Z value is not 20.0")
    check.equal(summary["minZ"], 4.5, "Lowest Z value is not 5.0")

    result = payload["result"]

    check.equal(result["bucket"], "basebucket", "Bucket is not basebucket")
    csv_obj = client.get_object(bucket_name="basebucket", object_name=result['objectKey'])
    df = pd.read_csv(csv_obj)

    expected_points = {
        (0.0, 0.0): 6,    # 4 points clustered near (0.5,0.5)
        (2.0, 2.0): 3,    # 3 points near (2.0, 2.5)
        (9.0, 9.0): 3,    # 3 points near (9.5, 9.2)
    }

    expected_z = {
        (0.0, 0.0): {"min": 4.5, "max": 10.0},
        (2.0, 2.0): {"min": 18.0, "max": 20.0},
        (9.0, 9.0): {"min": 14.5, "max": 19.0},
    }

    expected_veg_height = {
        (0.0, 0.0): {"min": 1.4, "max": 1.6},
        (2.0, 2.0): {"min": 3.5, "max": 3.7},
        (9.0, 9.0): {"min": 4.5, "max": 4.7},
    }


    for (expected_x0, expected_y0), expected_count in expected_points.items():
        filtered_df = df[(df["x0"] == expected_x0)& (df["y0"] == expected_y0)]

        check.equal(len(filtered_df), 1)

        row = filtered_df.iloc[0]

        check.equal(row["count"], expected_count,
                    f"Count mismatch for cell ({expected_x0}, {expected_y0}): expected {expected_count}, got {row['count']}")


@pytest.mark.e2e
def test_precompute_integration_with_disjoint_bboxes(minio_client, rabbitmq_ch, load_json, very_small_las_file):
    client, upload_file = minio_client
    assert client.bucket_exists("basebucket")
    upload_file(very_small_las_file, object_name="small_split.las")

    def run_worker():
        try:
            preprocess_worker.main()
        except SystemExit:
            pass

    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()
    test_msg = load_json("valid_precompute_job_small_las_file.json")
    test_msg["file"] = {
        "bucket": "basebucket",
        "objectKey": "small_split.las"
    }
    test_msg["jobId"] = "bbox-integration-test-1"
    test_msg["bboxes"] = [
        {
            "xMin": 0.0,
            "xMax": 1.5,
            "yMin": 0.0,
            "yMax": 1.5
        },
        {
            "xMin": 9.0,
            "xMax": 10.0,
            "yMin": 9.0,
            "yMax": 10.0
        }
    ]
    publish_message(rabbitmq_ch, WORKER_EXCHANGE, PREPROCESSING_JOB_RK, test_msg)
    body = consume_single_message(rabbitmq_ch, PREPROCESSING_RESULT_QUEUE)
    assert body is not None, "Worker did not return a result"
    response = json.loads(body)
    check.equal(response["status"], "success", f"Job failed: {response.get('payload')}")
    check.equal(response["job_id"], "bbox-integration-test-1")
    payload = response["payload"]
    summary = payload["summary"]

    result = payload["result"]
    csv_obj = client.get_object(bucket_name="basebucket", object_name=result['objectKey'])
    df = pd.read_csv(csv_obj)
    cell_0_0 = df[(df["x0"] == 0.0) & (df["y0"] == 0.0)]
    check.equal(len(cell_0_0), 1, "Cell (0,0) missing")
    if not cell_0_0.empty:
        check.equal(cell_0_0["count"].iloc[0], 2, "Cell (0,0) should have 2 points (Box 1)")

    cell_9_9 = df[(df["x0"] == 9.0) & (df["y0"] == 9.0)]
    check.equal(len(cell_9_9), 1, "Cell (9,9) missing")
    if not cell_9_9.empty:
        check.equal(cell_9_9["count"].iloc[0], 1, "Cell (9,9) should have 1 point (Box 2)")

    cell_2_2 = df[(df["x0"] == 2.0) & (df["y0"] == 2.0)]
    if not cell_2_2.empty:
        count_val = cell_2_2["count"].iloc[0]
        check.is_true(count_val == 0 or pd.isna(count_val),
                      f"Cell (2,2) should be empty/zero because it was excluded by bbox! Found count: {count_val}")
    else:
        pass
    total_count_in_df = df["count"].sum()
    check.equal(total_count_in_df, 3, "Total processed points should be 3 (1 point filtered out)")
