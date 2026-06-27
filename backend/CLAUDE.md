# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./mvnw clean install

# Run the application (requires LocalStack running first)
docker compose up -d        # start LocalStack (S3 on port 4566)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=DocumentSummaryAssistantBackendApplicationTests

# Package as WAR
./mvnw clean package
```

## Architecture

Spring Boot 4.1.0 backend (Java 21, Maven, WAR packaging) for a document summary assistant. Currently implements file upload/download via S3-compatible storage (LocalStack for local dev).

**Request flow:** `FileManagerControllerImpl` (REST endpoints under `/upload`) → `S3Service` (S3 operations) → `S3Client` bean (configured in `S3Config` to point at LocalStack).

- **S3Config** creates the `S3Client` bean manually with LocalStack endpoint, credentials, and `forcePathStyle(true)`. The `spring-cloud-aws-starter-s3` dependency also auto-configures S3 beans, but this custom bean takes precedence.
- **S3Service** auto-creates the configured bucket on startup (`@PostConstruct`), and handles file uploads with UUID-prefixed keys.
- **SummarizableDocument** model is a placeholder — not yet implemented.
- Spring AI Anthropic dependency is commented out in `pom.xml` — planned for future summarization features.
- H2 in-memory database is configured via Spring Data JPA but not actively used yet.

## Local Development

LocalStack must be running before starting the app. The S3 config in `application.properties` uses dummy credentials (`test`/`test`) and endpoint `http://localhost:4566`.
