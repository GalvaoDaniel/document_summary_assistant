package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    public String extractText(Resource document) {
        List<Document> docs;

        String fileName = document.getFilename();
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            docs = new PagePdfDocumentReader(document).get();
        } else {
            docs = new TextReader(document).get();
        }

        return docs.stream().map(Document::getText).collect(Collectors.joining("\n"));
    }
}
