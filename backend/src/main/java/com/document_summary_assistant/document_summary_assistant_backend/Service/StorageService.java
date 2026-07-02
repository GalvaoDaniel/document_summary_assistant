package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class StorageService {

    private final StorageStrategy storageStrategy;

    public StorageService(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }

    public String upload(MultipartFile file) throws IOException {
        return storageStrategy.upload(file);
    }
}
