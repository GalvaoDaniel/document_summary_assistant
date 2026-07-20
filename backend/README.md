# document-summary-assistant-backend

Spring Boot module that handles document upload/download and AI summarization. Storage is pluggable through a strategy chosen by Spring profile: the local filesystem, or S3 (backed by LocalStack in development). Summaries are produced through Spring AI against Anthropic's Claude models.

- **Group / artifact:** `com.document-summary-assistant` / `document-summary-assistant-backend`
- **Version:** `0.0.1-SNAPSHOT`
- **Packaging:** WAR (deployable to an external servlet container, or runnable standalone via the embedded Tomcat)

## Requirements

- JDK 21
- An Anthropic API key in the `ANTHROPIC_API_KEY` environment variable — required for the summary endpoint (upload/download work without it)
- Docker, only if you intend to run the `aws` profile against LocalStack

Maven itself is not required — use the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Quick start

The default `local` profile needs no external services:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080` and writes uploads to `./uploads/`.

To exercise the S3 path instead, start LocalStack first:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=aws
```

## Architecture

Two request flows share the storage layer.

**Upload / download** (`/upload`):

```
FileManagerControllerImpl  →  StorageService  →  StorageStrategy
                              (thin delegate)     ├── FileSystemStrategy  (@Profile("local"))
                                                  └── AwsS3Strategy       (@Profile("aws"))
                                                            │
                                                            └→ S3Client   (built in S3Config)
```

**Summarize** (`/api/summaries`):

```
SummaryControllerImpl  →  SummaryService  →  StorageService.serveFile  (load the file)
                                          →  DocumentService.extractText (PDF or plain text)
                                          →  ChatClient                  (Spring AI → Anthropic)
```

| Class                       | Package         | Role                                                                                                                     |
| --------------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `FileManagerController`     | `Controller`    | Interface declaring `saveFile`.                                                                                          |
| `FileManagerControllerImpl` | `Controller`    | REST endpoints under `/upload`: upload, and download via `StorageService.serveFile`. Maps the empty-file case to `400` and `IOException` to `500`. |
| `SummaryController`         | `Controller`    | Interface declaring `getSummary`.                                                                                        |
| `SummaryControllerImpl`     | `Controller`    | `GET /api/summaries/{fileName}` — returns the generated summary as plain text.                                           |
| `StorageService`            | `Service`       | Injected with whichever `StorageStrategy` bean the active profile supplies; delegates `upload` and `serveFile` to it.     |
| `StorageStrategy`           | `Service`       | Interface: `String upload(MultipartFile)` and `Resource serveFile(String)`.                                              |
| `FileSystemStrategy`        | `Service`       | `@Profile("local")`. Creates the upload dir if needed and writes the file under its original name. Serves it back as a `UrlResource`. |
| `AwsS3Strategy`             | `Service`       | `@Profile("aws")`. Creates the bucket on startup (`@PostConstruct`) if absent, uploads under a `<uuid>-<name>` key, and serves objects as an `InputStreamResource`. |
| `DocumentService`           | `Service`       | Extracts text from a `Resource`: PDFs via Spring AI's `PagePdfDocumentReader`, everything else via `TextReader`.          |
| `SummaryService`            | `Service`       | Orchestrates load → extract → prompt Claude, and returns the summary text.                                               |
| `S3Config`                  | `Configuration` | Builds the `S3Client` bean with an endpoint override, static credentials, and `forcePathStyle(true)`.                     |
| `ChatClientConfig`          | `Configuration` | Builds the `ChatClient` bean from the auto-configured `ChatClient.Builder`.                                              |
| `AppProperties`             | `Configuration` | `@ConfigurationProperties(prefix = "app")` record exposing `uploadDir` (`app.upload-dir`).                              |
| `SummarizableDocument`      | `Model`         | Empty placeholder.                                                                                                        |
| `ServletInitializer`        | (root)          | WAR bootstrap for deployment to an external container.                                                                    |

Three details worth knowing before you change things here:

- **Exactly one `StorageStrategy` bean must exist.** `StorageService` takes a single `StorageStrategy` by constructor injection, so activating both profiles at once, or neither, fails at startup. A new strategy needs its own distinct profile.
- **`S3Config` is not profile-gated.** The `S3Client` bean is created under every profile, including `local` where nothing injects it. It's inert there, but it does mean `aws.s3.*` properties must resolve regardless of profile.
- **Spring AI only auto-configures a `ChatClient.Builder`, not a `ChatClient`.** `ChatClientConfig` bridges that gap by building the client bean. Without it, `SummaryService`'s constructor injection of `ChatClient` fails at startup with an unsatisfied-dependency error.

## Endpoints

### `POST /upload`

`multipart/form-data`, file in the `file` field.

```bash
curl -F "file=@document.pdf" http://localhost:8080/upload
```

| Status | Body                                                                             |
| ------ | -------------------------------------------------------------------------------- |
| `200`  | `File saved at: <absolute path>` (`local`) or the S3 object key (`aws`)          |
| `400`  | `No File Selected` — the uploaded part was empty                                  |
| `500`  | `Error: <message>` — the write failed                                             |

Uploads above `5MB` are rejected by Spring's multipart layer before reaching the controller.

### `GET /upload/files/{fileName}`

Returns the file as an attachment (`Content-Disposition: attachment`). It resolves `fileName` through `StorageService`, so it serves files from whichever strategy the active profile uses — the local `uploads/` directory, or S3 under the `aws` profile.

```bash
curl -O http://localhost:8080/upload/files/document.pdf
```

### `GET /api/summaries/{fileName}`

Loads the named file through the storage layer, extracts its text, and returns an AI-generated summary as plain text. Requires `ANTHROPIC_API_KEY` to be set.

```bash
curl http://localhost:8080/api/summaries/document.pdf
```

## Testing `/upload` against the `aws` profile

End-to-end check that a file really lands in S3, using LocalStack.

**1. Start LocalStack** and confirm it's healthy — the S3 service needs to be up before the app boots, since `AwsS3Strategy` creates the bucket during startup:

```bash
docker compose up -d
docker compose ps                       # localstack should read "running"
curl http://localhost:4566/_localstack/health
```

**2. Start the app on the `aws` profile:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=aws
```

On startup `AwsS3Strategy.initBucket()` creates `my-bucket` if it isn't there yet.

**3. Upload a file.** The response body is the object key, in the form `<uuid>-<original-filename>`:

```bash
curl -F "file=@document.pdf" http://localhost:8080/upload
# → 3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf
```

**4. Check the object inside the LocalStack container.** The image ships with `awslocal`, a wrapper that targets the local endpoint for you:

```bash
docker compose exec localstack awslocal s3 ls s3://my-bucket/
```

Use `docker compose exec` rather than `docker exec <name>`: it resolves the `localstack` service by name, so it keeps working regardless of the generated container name (`backend-localstack-1`, unless the compose project name is overridden).

For object metadata — size, content type, last modified — query the key returned in step 3:

```bash
docker compose exec localstack awslocal s3api head-object \
  --bucket my-bucket --key 3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf
```

And to pull the object back out and diff it against the original, confirming the bytes survived the round trip:

```bash
docker compose exec localstack awslocal s3 cp \
  s3://my-bucket/3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf /tmp/roundtrip.pdf
docker compose cp localstack:/tmp/roundtrip.pdf ./roundtrip.pdf
diff document.pdf roundtrip.pdf && echo "identical"
```

If you have the AWS CLI on the host, the same checks work without entering the container by pointing it at the LocalStack endpoint:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://my-bucket/
```

**Note:** under the `aws` profile these objects are also reachable through `GET /upload/files/{fileName}` (using the object key as `fileName`) and can be summarized via `GET /api/summaries/{fileName}`. Keep in mind that LocalStack's S3 data is in-memory here, since no volume is mounted for it in `docker-compose.yml`; `docker compose down` discards every uploaded object.

## Configuration

`src/main/resources/application.properties`:

| Property                                     | Default                 | Notes                                                    |
| -------------------------------------------- | ----------------------- | -------------------------------------------------------- |
| `spring.application.name`                    | `document-summary-assistant-backend` |                                             |
| `spring.profiles.active`                     | `local`                 | Selects the storage strategy: `local` or `aws`            |
| `spring.servlet.multipart.max-file-size`     | `5MB`                   | Per-file limit                                            |
| `spring.servlet.multipart.max-request-size`  | `5MB`                   | Whole-request limit                                       |
| `app.upload-dir`                             | `./uploads`             | Directory the `local` strategy reads from and writes to   |
| `spring.cloud.aws.region.static`             | `us-east-1`             | Region for the Spring Cloud AWS autoconfiguration         |
| `aws.s3.endpoint`                            | `http://localhost:4566` | LocalStack in dev; drop the override for real AWS         |
| `aws.s3.region`                              | `us-east-1`             | Region used by the `S3Client` bean                        |
| `aws.s3.bucket`                              | `my-bucket`             | Created on startup under the `aws` profile if missing     |
| `aws.s3.access-key` / `aws.s3.secret-key`    | `test` / `test`         | Dummy values; LocalStack does not validate credentials    |
| `spring.ai.anthropic.api-key`                | `${ANTHROPIC_API_KEY}`  | Read from the environment; the summary endpoint needs it  |
| `spring.ai.anthropic.chat.options.model`     | `claude-sonnet-4-5`     | Model used for summarization                              |
| `spring.ai.anthropic.chat.options.max-tokens`| `1024`                  | Max tokens per summary response                           |

The committed credentials are placeholders for LocalStack. For real AWS, supply them through environment variables or another external source rather than editing them into this file. The same goes for `ANTHROPIC_API_KEY`, which is intentionally not stored in the repo.

> **Gotcha — `app.upload-dir` has no trailing slash.** `FileSystemStrategy.upload` builds the target path by string-concatenating `app.upload-dir` with the filename (`props.uploadDir() + file.getOriginalFilename()`), while `serveFile` resolves it as a directory (`Paths.get(uploadDir).resolve(filename)`). With the default `./uploads` (no trailing slash) these two disagree, so a plain rename of the property value can leave written and served paths out of sync. Adjust both call sites together if you touch this.

## Build and test

```bash
./mvnw clean install                 # full build
./mvnw test                          # all tests
./mvnw test -Dtest=DocumentSummaryAssistantBackendApplicationTests   # single class
./mvnw clean package                 # WAR into target/
```

Test coverage is currently limited to the application context-load check in `DocumentSummaryAssistantBackendApplicationTests`. Note that this test loads the full context, so it needs `ANTHROPIC_API_KEY` present (or the Anthropic auto-configuration relaxed) to pass.

Lombok is wired in as an annotation processor via `maven-compiler-plugin` and excluded from the packaged artifact by `spring-boot-maven-plugin`. If your IDE reports missing generated methods, enable annotation processing in it.

## Dependencies of note

- `spring-cloud-aws-starter-s3` (4.0.0) — also autoconfigures S3 beans, but the explicit `S3Client` in `S3Config` takes precedence.
- `spring-ai-starter-model-anthropic` — provides the `ChatClient.Builder` and Anthropic chat model. Managed by the `spring-ai-bom` (`2.0.0`) imported in `dependencyManagement`.
- `spring-ai-pdf-document-reader` — `PagePdfDocumentReader`, used by `DocumentService` to extract text from PDFs.
- `spring-boot-starter-data-jpa` + H2 (runtime) — configured but not yet used; no entities or repositories exist so far.

## Roadmap

- Flesh out `SummarizableDocument`, currently an empty class
- Persist document metadata and generated summaries using the already-configured JPA/H2 setup
- Reconcile the `app.upload-dir` concatenation between `FileSystemStrategy.upload` and `serveFile` (see the gotcha above)
- Broaden `DocumentService` beyond PDF and plain text
