package com.document_summary_assistant.document_summary_assistant_backend.Controller;

import com.document_summary_assistant.document_summary_assistant_backend.Service.SummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summaries")
public class SummaryControllerImpl implements SummaryController {

    private final SummaryService summaryService;

    public SummaryControllerImpl(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<String> getSummary(@PathVariable String fileName) {
        String summary = summaryService.summarize(fileName);
        return ResponseEntity.ok(summary);
    }
}
