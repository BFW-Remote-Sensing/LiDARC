schema = {
    "type": "object",
    "required": ["jobId", "file", "grid", "bboxes"],
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
        "bboxes": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "required": ["xMin", "xMax", "yMin", "yMax"],
                "properties": {
                    "xMin": { "type": "number" },
                    "xMax": { "type": "number" },
                    "yMin": { "type": "number" },
                    "yMax": { "type": "number" },
                },
                "additionalProperties": False
            }
        },
        "comparisonId": {
            "type": "integer",
            "minimum": 1
        },
        "fileId": {
            "type": "integer",
            "minimum": 1
        },
        "individualPercentile" : {
            "type": "number",
            "minimum": 0.01,
            "maximum": 99.99
        }
    },
    "additionalProperties": False
}