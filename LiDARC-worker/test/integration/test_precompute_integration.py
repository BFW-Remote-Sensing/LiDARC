import json
import threading
from http import HTTPMethod

import pika
import pytest
import pytest_check as check
import pandas as pd
import preprocess.preprocess_worker as preprocess_worker

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
    presigned_url = client.get_presigned_url(method=HTTPMethod.GET, bucket_name="basebucket", object_name="small.las")

    def run_worker():
        preprocess_worker.main()
    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_msg = load_json("valid_precompute_job_small_las_file.json")
    test_msg["url"] = presigned_url
    publish_message(rabbitmq_ch, "worker.job", "preprocessing.job", test_msg)
    body = consume_single_message(rabbitmq_ch, "preprocessing.result")
    assert body is not None, "Result message of preprocess worker is None"
    response = json.loads(body)

    check.equal(response["status"], "success", "Status is not success")
    check.equal(response["jobId"], "12345", "JobId is")

    summary = response["summary"]
    check.equal(summary["nCells"], 100, "Number of cells is not 100 cells")
    check.equal(summary["maxZ"], 20.0, "Highest Z value is not 20.0")
    check.equal(summary["minZ"], 5.0, "Lowest Z value is not 5.0")
    result = response["result"]
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

        veg_p90 = filtered_df["veg_p90"].iloc[0]
        veg_p95 = filtered_df["veg_p95"].iloc[0]
        print(veg_p90, veg_p95)
        assert isinstance(veg_p90, float) and not pd.isna(veg_p90), f"veg_p90 is invalid for cell ({expected_x0}, {expected_y0})"
        assert isinstance(veg_p95, float) and not pd.isna(veg_p95), f"veg_p95 is invalid for cell ({expected_x0}, {expected_y0})"

        if expected_count > 1:
            assert veg_p90 >= 0.0, f"veg_p90 suspiciously low for cell ({expected_x0}, {expected_y0})"
            assert veg_p95 >= veg_p90, f"veg_p95 should be >= veg_p90 for cell ({expected_x0}, {expected_y0})"


@pytest.mark.e2e
def test_precompute_integration_with_small_las_file(minio_client, rabbitmq_ch, load_json, small_las_file):
    client, upload_file = minio_client
    assert client.bucket_exists("basebucket")
    upload_file(small_las_file, object_name="small.las")
    presigned_url = client.get_presigned_url(method=HTTPMethod.GET, bucket_name="basebucket", object_name="small.las")

    def run_worker():
        preprocess_worker.main()
    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_msg = load_json("valid_precompute_job_small_las_file.json")
    test_msg["url"] = presigned_url
    publish_message(rabbitmq_ch, "worker.job", "preprocessing.job", test_msg)
    body = consume_single_message(rabbitmq_ch, "preprocessing.result")
    assert body is not None, "Result message of preprocess worker is None"
    response = json.loads(body)

    check.equal(response["status"], "success", "Status is not success")
    check.equal(response["jobId"], "12345", "JobId is")

    summary = response["summary"]
    check.equal(summary["nCells"], 100, "Number of cells is not 100 cells")
    check.equal(summary["maxZ"], 20.0, "Highest Z value is not 20.0")
    check.equal(summary["minZ"], 4.5, "Lowest Z value is not 5.0")

    result = response["result"]

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

    expected_percentiles = {
        (0.0, 0.0): {"p90_min": 1.55, "p90_max": 2.5, "p95_min": 1.58, "p95_max": 2.5},
        (2.0, 2.0): {"p90_min": 3.6, "p90_max": 3.7, "p95_min": 3.7, "p95_max": 3.7},
        (9.0, 9.0): {"p90_min": 4.6, "p90_max": 4.7, "p95_min": 4.7, "p95_max": 4.7},
    }
    for (expected_x0, expected_y0), expected_count in expected_points.items():
        filtered_df = df[(df["x0"] == expected_x0)& (df["y0"] == expected_y0)]

        check.equal(len(filtered_df), 1)

        row = filtered_df.iloc[0]

        check.equal(row["count"], expected_count,
                    f"Count mismatch for cell ({expected_x0}, {expected_y0}): expected {expected_count}, got {row['count']}")

        veg_p90 = row["veg_p90"]
        veg_p95 = row["veg_p95"]

        print(veg_p90, veg_p95)

        assert isinstance(veg_p90, float) and not pd.isna(veg_p90), f"veg_p90 invalid for cell ({expected_x0}, {expected_y0})"
        assert isinstance(veg_p95, float) and not pd.isna(veg_p95), f"veg_p95 invalid for cell ({expected_x0}, {expected_y0})"

        if (expected_x0, expected_y0) in expected_percentiles:
            exp = expected_percentiles[(expected_x0, expected_y0)]
            assert exp["p90_min"] <= veg_p90 <= exp["p90_max"], \
                f"veg_p90 {veg_p90} out of expected range for cell ({expected_x0}, {expected_y0})"
            assert exp["p95_min"] <= veg_p95 <= exp["p95_max"], \
                f"veg_p95 {veg_p95} out of expected range for cell ({expected_x0}, {expected_y0})"