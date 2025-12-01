package com.example.lidarcbackend.service.files;

import java.util.Map;

public interface IMetadataService {

    /**
     * Processes a metadata worker result message
     *
     * @param result the result message from the worker; can either be success message or error message
     */
    void processMetadata(Map<String, Object> result);
}
