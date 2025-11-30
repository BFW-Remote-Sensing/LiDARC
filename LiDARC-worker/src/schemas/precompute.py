schema = {
    "type": "object",
    "required": ["jobId", "url", "grid"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "url": {
            "type": "string"
        },
        "grid": {
            "type": "object",
            "required": ["xMin", "xMax", "yMin", "yMax", "x", "y"],
            "properties": {
                "xMin": { "type": "number" },
                "xMax": { "type": "number" },
                "yMin": { "type": "number" },
                "yMax": { "type": "number" },
                "x": { "type": "integer", "minimum": 1 },
                "y": { "type": "integer", "minimum": 1 },
                "offset": { "type": "number" }
            },
            "additionalProperties": False
        }
    },
    "additionalProperties": False
}