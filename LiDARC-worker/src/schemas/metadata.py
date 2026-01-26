schema = {
    "type": "object",
    "required": ["jobId", "url", "fileName"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "url": {
            "type": "string"
        },
        "fileName": {
            "type": "string"
        }
    },
    "additionalProperties": False
}