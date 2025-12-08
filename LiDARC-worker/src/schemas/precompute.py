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
            "required": ["xmin", "xmax", "ymin", "ymax", "cellWidth", "cellHeight"],
            "properties": {
                "xmin": { "type": "number" },
                "xmax": { "type": "number" },
                "ymin": { "type": "number" },
                "ymax": { "type": "number" },
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