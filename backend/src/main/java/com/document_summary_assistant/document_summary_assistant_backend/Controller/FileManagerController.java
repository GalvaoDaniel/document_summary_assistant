package com.document_summary_assistant.document_summary_assistant_backend.Controller;

import com.document_summary_assistant.document_summary_assistant_backend.Model.SummarizableDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface FileManagerController {

    public ResponseEntity<String> saveFile(MultipartFile file);
}
