package com.example.lidarcbackend.service.files;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class MetadataService implements IMetadataService {

    @Override
    public void processMetadata(Map<String, Object> metadata) {
        log.info("Processing Metadata result:");
        metadata.forEach((k, v) -> log.info("{} = {}", k, v));
    }
}
