# Document Summary Assistant

A Spring Boot backend for uploading documents and generating AI summaries of them. Storage is pluggable (local filesystem or S3), and summarization runs through Spring AI with Anthropic's Claude models.

## Stack

- Java 21
- Spring Boot 4.1.0 (Web MVC, Data JPA)
- Maven (WAR packaging)
- Spring AI (`spring-ai-starter-model-anthropic`, `spring-ai-pdf-document-reader`) for text extraction and summarization
- AWS SDK for S3 via `spring-cloud-aws-starter-s3`
- H2 in-memory database (configured, not used yet)
- LocalStack to emulate S3 during development

## Structure

```
backend/
├── src/main/java/.../
│   ├── Configuration/
│   │   ├── S3Config.java                     # S3Client bean pointing at the configured endpoint
│   │   ├── ChatClientConfig.java             # builds the ChatClient bean from Spring AI's builder
│   │   └── AppProperties.java                # @ConfigurationProperties record (app.upload-dir)
│   ├── Controller/
│   │   ├── FileManagerControllerImpl.java    # upload/download endpoints under /upload
│   │   └── SummaryControllerImpl.java        # summary endpoint under /api/summaries
│   ├── Model/SummarizableDocument.java       # placeholder, still empty
│   └── Service/
│       ├── StorageService.java               # delegates to the active storage strategy
│       ├── StorageStrategy.java              # storage interface (upload + serveFile)
│       ├── FileSystemStrategy.java           # "local" profile: writes to uploads/
│       ├── AwsS3Strategy.java                # "aws" profile: writes to S3/LocalStack
│       ├── DocumentService.java              # extracts text (PDF or plain text) from a file
│       └── SummaryService.java               # loads a file, extracts text, asks Claude to summarize
├── docker-compose.yml                        # LocalStack (S3 on port 4566)
└── pom.xml
```

## How it works

1. A file is uploaded through `POST /upload` and stored via the active storage strategy.
2. `GET /api/summaries/{fileName}` loads that file back through `StorageService`, `DocumentService` extracts its text (PDF pages via Spring AI's PDF reader, everything else as plain text), and `SummaryService` sends the text to Claude via the Spring AI `ChatClient`, returning the summary.

## Storage strategies

The strategy is selected by Spring profile, and only one is loaded at a time:

| Profile | Implementation       | Where the file ends up                                  |
| ------- | -------------------- | ------------------------------------------------------- |
| `local` | `FileSystemStrategy` | The `uploads/` directory, keeping the original filename |
| `aws`   | `AwsS3Strategy`      | An S3 bucket, keyed as `<uuid>-<original-filename>`     |

The active profile is set in `application.properties` (`spring.profiles.active=local`). Under the `aws` profile, the configured bucket is created automatically on startup if it doesn't already exist.

## Running

Summarization requires an Anthropic API key, exposed as the `ANTHROPIC_API_KEY` environment variable:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Upload and download work without it, but any call to the summary endpoint needs it set.

### `local` profile (default)

Requires neither Docker nor LocalStack:

```bash
cd backend
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` and writes files to `backend/uploads/`.

### `aws` profile (LocalStack)

Start LocalStack before the app:

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=aws
```

## Endpoints

### `POST /upload`

Uploads a file as `multipart/form-data` under the `file` field. Returns an identifier for the stored file: a message with the absolute path under the `local` profile, or the object key under `aws`.

```bash
curl -F "file=@document.pdf" http://localhost:8080/upload
```

Responds `400` when no file is sent, and `500` on a write failure.

### `GET /upload/files/{fileName}`

Downloads a stored file as an attachment. It resolves the file through `StorageService`, so it works under both profiles.

```bash
curl -O http://localhost:8080/upload/files/document.pdf
```

### `GET /api/summaries/{fileName}`

Loads the named file, extracts its text, and returns an AI-generated summary as plain text. Requires `ANTHROPIC_API_KEY`.

```bash
curl http://localhost:8080/api/summaries/document.pdf
```

## Configuration

Defined in `backend/src/main/resources/application.properties`:

| Property                                       | Default                 | Description                                |
| ---------------------------------------------- | ----------------------- | ------------------------------------------ |
| `spring.profiles.active`                       | `local`                 | Active storage strategy                    |
| `spring.servlet.multipart.max-file-size`       | `5MB`                   | Maximum size per file                      |
| `app.upload-dir`                               | `./uploads`             | Directory used by the `local` strategy     |
| `aws.s3.endpoint`                              | `http://localhost:4566` | S3 endpoint (LocalStack in dev)            |
| `aws.s3.region`                                | `us-east-1`             | S3 region                                  |
| `aws.s3.bucket`                                | `my-bucket`             | Target bucket                              |
| `aws.s3.access-key` / `aws.s3.secret-key`      | `test` / `test`         | Dummy credentials, accepted by LocalStack  |
| `spring.ai.anthropic.api-key`                  | `${ANTHROPIC_API_KEY}`  | Anthropic API key, read from the environment |
| `spring.ai.anthropic.chat.options.model`       | `claude-sonnet-4-5`     | Model used for summarization               |
| `spring.ai.anthropic.chat.options.max-tokens`  | `1024`                  | Max tokens per summary response            |

To run against real AWS, replace the endpoint and credentials with valid values — preferably through environment variables rather than committing them to the repository.

## Build and tests

```bash
cd backend

./mvnw clean install   # full build
./mvnw test            # tests
./mvnw clean package   # produces the WAR in target/
```

## Roadmap

- Implement the `SummarizableDocument` model, currently just a placeholder
- Persist document metadata and generated summaries (JPA and H2 are already configured but unused)
- Support additional document formats in `DocumentService`
