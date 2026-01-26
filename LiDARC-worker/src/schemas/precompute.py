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
        },
        "pointFilterLowerBound": {
            "type": ["number", "null"],
            "minimum": 0,
            "maximum": 100
        },
        "pointFilterUpperBound": {
            "type": ["number", "null"],
            "minimum": 0,
            "maximum": 100
        },
        "pointFilterEnabled": {
            "type": "boolean"
        },
        "outlierDetectionEnabled": {
            "type": "boolean"
        },
        "outlierDeviationFactor": {
            "anyOf": [
                {"type": "number", "minimum": 0},
                {"type": "null"}
            ]
        }
    },
    "additionalProperties": False
}