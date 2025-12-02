schema = {
    "type": "object",
    "required": ["jobId", "url"],
    "properties": {
        "jobId": {
            "type": "string",
            "minLength": 1
        },
        "url": {
            "type": "string"
        }
    },
    "additionalProperties": False
}