schema = {
    "type": "object",
    "required": ["jobId", "file", "grid"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "file": {
            "type": "object",
            "required": ["bucket", "objectKey"],
            "properties": {
                "bucket": {"type": "string"},
                "objectKey": {"type": "string"}
            }
        },
        "grid": {
            "type": "object",
            "required": ["xMin", "xMax", "yMin", "yMax", "cellWidth", "cellHeight"],
            "properties": {
                "xMin": { "type": "number" },
                "xMax": { "type": "number" },
                "yMin": { "type": "number" },
                "yMax": { "type": "number" },
                "cellWidth": { "type": "integer", "minimum": 1 },
                "cellHeight": { "type": "integer", "minimum": 1 },
            },
            "additionalProperties": False
        },
        "comparisonId": {
            "type": "integer",
            "minimum": 1
        },
        "fileId": {
            "type": "integer",
            "minimum": 1
        }
    },
    "additionalProperties": False
}