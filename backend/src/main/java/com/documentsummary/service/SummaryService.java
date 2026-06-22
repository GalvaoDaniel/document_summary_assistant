package com.documentsummary.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SummaryService {

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;

    public SummaryService(ChatClient.Builder chatClientBuilder, SimpleVectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamSummary(Long documentId) {
        var filterExpression = new FilterExpressionBuilder()
                .eq("documentId", documentId.toString())
                .build();

        var searchRequest = SearchRequest.builder()
                .filterExpression(filterExpression)
                .topK(10)
                .build();

        return chatClient.prompt()
                .advisors(new QuestionAnswerAdvisor(vectorStore, searchRequest))
                .user("""
                        Summarize the document provided in the context. \
                        Produce a clear, well-structured summary in Markdown format. \
                        Include the main topics and key points. \
                        If the document is short, keep the summary concise. \
                        If the document is long, provide a comprehensive summary with sections.""")
                .stream()
                .content();
    }
}
