schema = {
    "type": "object",
    "required": ["jobId", "files"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "files": {
            "type": "array",
            "minItems": 2,
            "maxItems": 2,
            "items": {
                "type": "object",
                "required": ["originalFileName", "url"],
                "properties": {
                    "originalFileName": {
                        "type": "string",
                        "minLength": 1
                    },
                    "url": {
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