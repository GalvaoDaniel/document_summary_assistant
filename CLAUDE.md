# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI-powered document summarization assistant using RAG. Users upload PDF/text files, the backend extracts text, chunks it, generates embeddings, stores them in a vector store, and produces LLM-generated summaries streamed via SSE to a React frontend.

## Build & Run Commands

### Backend (`backend/`)
- **Build:** `./mvnw clean package` (or `mvn clean package`)
- **Run:** `./mvnw spring-boot:run` (runs on port 8080)
- **Run tests:** `./mvnw test`
- **Single test:** `./mvnw test -Dtest=ClassName#methodName`
- **Requires:** `OPENAI_API_KEY` environment variable

### Frontend (`frontend/`)
- **Install:** `npm install`
- **Dev server:** `npm run dev` (runs on port 5173)
- **Build:** `npm run build`
- **Type check:** `npx tsc --noEmit`
- **Lint:** `npm run lint`

## Architecture

```
React SPA (Vite)  ──HTTP/SSE──>  Spring Boot API  ──>  Spring AI  ──>  OpenAI
                                      │
                                      ├─> PDF extraction (PagePdfDocumentReader)
                                      ├─> Chunking (TokenTextSplitter) + Embeddings
                                      └─> SimpleVectorStore (in-memory, persisted to JSON)
```

- **H2** is used only for document metadata (id, name, status, upload date), not for vectors.
- **SimpleVectorStore** stores embeddings in a JSON file (`data/vector-store.json`). To evolve, swap for PGVector.
- **Streaming:** The summary endpoint returns `Flux<String>` as SSE (`TEXT_EVENT_STREAM_VALUE`). Only this endpoint is reactive; the rest is standard Spring MVC.
- **RAG:** Uses Spring AI's `QuestionAnswerAdvisor` to retrieve relevant chunks and inject them into the summarization prompt.

## API Endpoints

| Method | Route | Description |
|--------|-------|-------------|
| `POST` | `/api/documents` | Upload document (multipart) |
| `GET`  | `/api/documents` | List documents |
| `POST` | `/api/documents/{id}/summary` | Stream summary (SSE) |

## Frontend Components

- `FileUpload` — file selection + upload
- `DocumentList` — lists uploaded documents, fetches on mount
- `SummaryView` — triggers summarization, consumes SSE stream via `@microsoft/fetch-event-source`, renders Markdown incrementally with `react-markdown`

## Key Conventions

- Frontend is intentionally simple: no Redux, React Query, or heavy libraries. `useState`/`useEffect` only.
- CORS is configured to allow `http://localhost:5173` (Vite dev server).
