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
def test_precompute_integration(minio_client, rabbitmq_ch, load_json):
    assert minio_client.bucket_exists("basebucket")
    presigned_url = minio_client.get_presigned_url(method=HTTPMethod.GET, bucket_name="basebucket", object_name="small.las")

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
    csv_obj = minio_client.get_object(bucket_name="basebucket", object_name=result['objectKey'])
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
