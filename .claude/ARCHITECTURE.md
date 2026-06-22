# Document Summarization Assistant — Architecture

Technical specification to guide the creation of the main project structure (e.g., with Claude Code).

## Overview

An AI-powered document summarization application using RAG. The user uploads a PDF or text file, the backend extracts and processes the content, generates embeddings, retrieves the relevant chunks, and produces a summary via an LLM, streamed back to the frontend for incremental rendering.

## Main flow (RAG)

1. User uploads a PDF/text file (multipart upload).
2. Backend extracts the text from the file.
3. Text is split into chunks.
4. Embeddings are generated for the chunks.
5. Embeddings are stored in the vector store.
6. On a summary request: relevant chunks are retrieved and injected into the prompt.
7. The LLM generates the summary, streamed back via SSE.
8. The frontend renders the summary token by token.

```
React SPA  ──HTTP/SSE──>  Spring Boot API  ──>  Spring AI  ──>  LLM provider
                              │
                              ├─> Text extraction (Spring AI PDF Document Reader / PDFBox)
                              ├─> Chunking + Embeddings
                              └─> Vector Store (SimpleVectorStore)
```

## Backend

### Stack
- Java 21
- Spring Boot
- Spring MVC (REST API)
- Spring AI (chat + embeddings + RAG)
- Maven (dependency management)
- H2 (local development database, for metadata)

### Main Maven dependencies
- `spring-boot-starter-web` — REST API with Spring MVC
- `spring-ai-starter-model-openai` — chat and embedding models (replaceable with Anthropic/Ollama)
- `spring-ai-starter-vector-store-*` — vector store
- `spring-ai-pdf-document-reader` — PDF text extraction (wraps PDFBox)
- `spring-boot-starter-data-jpa` + `com.h2database:h2` — metadata persistence
- `spring-boot-starter-webflux` — required for the `Flux` type in the streaming endpoint

### Architecture decisions

**Vector store.** Use Spring AI's `SimpleVectorStore` (in-memory, persisted to JSON). H2 has no native support for vector search, so it is used only for document metadata (id, name, upload date, status). To evolve later, swap it for PGVector with Postgres.

**Streaming.** Spring AI exposes `ChatClient.stream()`, which returns a `Flux<String>`. The summary endpoint should produce `MediaType.TEXT_EVENT_STREAM_VALUE` (SSE) and return `Flux<String>`. Only the streaming endpoint is reactive; the rest follows traditional Spring MVC.

**RAG.** Leverage Spring AI's RAG capabilities (e.g., `QuestionAnswerAdvisor`) to automate the retrieval → prompt step. For the summarization case, retrieve the relevant chunks and inject them into a summarization prompt.

### Endpoints

| Method | Route | Description |
|--------|-------|-------------|
| `POST` | `/api/documents` | Upload a document (multipart). Returns `documentId`. |
| `GET`  | `/api/documents` | List uploaded documents. |
| `POST` | `/api/documents/{id}/summary` | Stream (SSE) the document summary. |

## Frontend

### Stack
- React + TypeScript (SPA)
- **Vite** as the build tool and dev server (`react-ts` template)
- **Native fetch API** for HTTP calls (no need for Axios)
- **`@microsoft/fetch-event-source`** to consume SSE over POST cleanly
- **react-markdown** to render the summary (which arrives as Markdown)
- Styling: plain CSS or Tailwind (optional, per preference)

### Guidelines
The frontend should be kept **simple**, as it is not the focus of the project. Avoid: Redux, React Query, Next.js, and heavy component libraries. For an SPA that uploads a file and displays a streamed summary, `useState`/`useEffect` are sufficient.

### Minimal components
- `FileUpload` — file selection and upload.
- `DocumentList` — listing of uploaded documents.
- `SummaryView` — consumes the stream and renders the summary incrementally.

### Incremental rendering
Accumulate the text received from the stream into a state buffer and render the Markdown of the accumulated buffer on each update — avoiding unnecessary re-renders on isolated tokens.
