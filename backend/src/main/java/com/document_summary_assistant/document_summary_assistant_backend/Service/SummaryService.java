package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

    // TODO refact to minimize the number of dependencies. Idea: define better contract for summarize method instead of bring all services
    private final ChatClient chatClient;
    private final DocumentService documentService;
    private final StorageService storageService;

    public SummaryService(ChatClient chatClient, DocumentService documentService, StorageService storageService) {
        this.chatClient = chatClient;
        this.documentService = documentService;
        this.storageService = storageService;
    }


    public String summarize(String fileName) {
        Resource document = storageService.serveFile(fileName);
        String documentContent = documentService.extractText(document);

        return chatClient.prompt().system("You are an assistant that summarizes documents clearly and objectively.")
                .user(u -> u.text("Summarize the following document:\n\n{doc}").param("doc", documentContent))
                .call().content();
    }
}
