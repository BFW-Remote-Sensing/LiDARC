schema = {
    "type": "object",
    "required": ["jobId", "url", "grid"],
    "properties": {
        "job_id": {
            "type": "string",
            "minLength": 1
        },
        "url": {
            "type": "string"
        },
        "grid": {
            "type": "object",
            "required": ["x_min", "x_max", "y_min", "y_max", "x", "y"],
            "properties": {
                "x_min": { "type": "number" },
                "x_max": { "type": "number" },
                "y_min": { "type": "number" },
                "y_max": { "type": "number" },
                "x": { "type": "integer", "minimum": 1 },
                "y": { "type": "integer", "minimum": 1 },
                "offset": { "type": "number" }
            },
            "additionalProperties": False
        }
    },
    "additionalProperties": False
}