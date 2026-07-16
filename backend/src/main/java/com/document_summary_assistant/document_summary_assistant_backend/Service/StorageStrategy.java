package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageStrategy {

    String upload(MultipartFile file) throws IOException;

    Resource serveFile(String fileName);
}
