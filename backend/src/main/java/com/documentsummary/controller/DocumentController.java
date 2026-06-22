package com.documentsummary.controller;

import com.documentsummary.model.Document;
import com.documentsummary.service.DocumentService;
import com.documentsummary.service.SummaryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final SummaryService summaryService;

    public DocumentController(DocumentService documentService, SummaryService summaryService) {
        this.documentService = documentService;
        this.summaryService = summaryService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        var document = documentService.uploadDocument(file);
        return ResponseEntity.ok(document);
    }

    @GetMapping
    public ResponseEntity<List<Document>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @PostMapping(value = "/{id}/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> summarizeDocument(@PathVariable Long id) {
        documentService.getDocument(id); // validates existence
        return summaryService.streamSummary(id);
    }
}
