package com.document_summary_assistant.document_summary_assistant_backend.Controller;

import org.springframework.http.ResponseEntity;

public interface SummaryController {

    ResponseEntity<String> getSummary(String fileName);
}
