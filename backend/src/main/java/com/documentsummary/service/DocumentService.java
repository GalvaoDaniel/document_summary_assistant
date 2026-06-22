package com.documentsummary.service;

import com.documentsummary.model.Document;
import com.documentsummary.model.DocumentStatus;
import com.documentsummary.repository.DocumentRepository;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final SimpleVectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;
    private final String vectorStorePath;

    public DocumentService(DocumentRepository documentRepository,
                           SimpleVectorStore vectorStore,
                           @Value("${app.chunk-size}") int chunkSize,
                           @Value("${app.chunk-overlap}") int chunkOverlap,
                           @Value("${app.vector-store-path}") String vectorStorePath) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.vectorStorePath = vectorStorePath;
    }

    public Document uploadDocument(MultipartFile file) throws IOException {
        var document = new Document(file.getOriginalFilename());
        document = documentRepository.save(document);

        try {
            var resource = new InputStreamResource(file.getInputStream());
            var fileName = file.getOriginalFilename();

            List<org.springframework.ai.document.Document> documents;
            if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                DocumentReader reader = new PagePdfDocumentReader(resource);
                documents = reader.get();
            } else {
                documents = List.of(new org.springframework.ai.document.Document(new String(file.getBytes())));
            }

            // Tag each chunk with the document ID for later retrieval
            String docId = document.getId().toString();
            for (var doc : documents) {
                doc.getMetadata().put("documentId", docId);
            }

            var splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
            var chunks = splitter.apply(documents);

            for (var chunk : chunks) {
                chunk.getMetadata().put("documentId", docId);
            }

            vectorStore.add(chunks);
            vectorStore.save(new java.io.File(vectorStorePath));

            document.setStatus(DocumentStatus.READY);
        } catch (Exception e) {
            document.setStatus(DocumentStatus.FAILED);
        }

        return documentRepository.save(document);
    }

    public List<Document> listDocuments() {
        return documentRepository.findAll();
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }
}
