schema = {
    "type": "object",
    "required": ["jobId", "comparisonId", "files"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "comparisonId": {
            "type": "string",
            "minLength": 1
        },
        "files": {
            "type": "array",
            "minItems": 2,
            "items": {
                "type": "object",
                "required": ["bucket", "objectKey", "groupName"],
                "properties": {
                    "bucket": {
                        "type": "string",
                        "minLength": 1
                    },
                    "objectKey": {
                        "type": "string",
                        "minLength": 1
                    },
                    "groupName": {
                        "type": "string",
                        "minLength": 1
                    }
                },
                "additionalProperties": False
            }
        }
    },
    "additionalProperties": False
}