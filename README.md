# Document Summary Assistant

A Spring Boot backend for uploading and downloading documents, with pluggable storage (local filesystem or S3). Summarization is not implemented yet — for now the project covers file management only.

## Stack

- Java 21
- Spring Boot 4.1.0 (Web MVC, Data JPA)
- Maven (WAR packaging)
- AWS SDK for S3 via `spring-cloud-aws-starter-s3`
- H2 in-memory database (configured, not used yet)
- LocalStack to emulate S3 during development

## Structure

```
backend/
├── src/main/java/.../
│   ├── Configuration/S3Config.java                # S3Client bean pointing at the configured endpoint
│   ├── Controller/FileManagerControllerImpl.java  # REST endpoints under /upload
│   ├── Model/SummarizableDocument.java            # placeholder, still empty
│   └── Service/
│       ├── StorageService.java                    # delegates to the active strategy
│       ├── StorageStrategy.java                   # storage interface
│       ├── FileSystemStrategy.java                # "local" profile: writes to uploads/
│       └── AwsS3Strategy.java                     # "aws" profile: writes to S3/LocalStack
├── docker-compose.yml                             # LocalStack (S3 on port 4566)
└── pom.xml
```

## Storage strategies

The strategy is selected by Spring profile, and only one is loaded at a time:

| Profile | Implementation       | Where the file ends up                                  |
| ------- | -------------------- | ------------------------------------------------------- |
| `local` | `FileSystemStrategy` | The `uploads/` directory, keeping the original filename |
| `aws`   | `AwsS3Strategy`      | An S3 bucket, keyed as `<uuid>-<original-filename>`     |

The active profile is set in `application.properties` (`spring.profiles.active=local`). Under the `aws` profile, the configured bucket is created automatically on startup if it doesn't already exist.

## Running

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

Uploads a file as `multipart/form-data` under the `file` field. Returns an identifier for the stored file: the absolute path under the `local` profile, or the object key under `aws`.

```bash
curl -F "file=@document.pdf" http://localhost:8080/upload
```

Responds `400` when no file is sent, and `500` on a write failure.

### `GET /upload/files/{fileName}`

Downloads a file from the `uploads/` directory as an attachment. Note that this endpoint reads straight from the filesystem, so it only works for files written by the `local` profile.

```bash
curl -O http://localhost:8080/upload/files/document.pdf
```

## Configuration

Defined in `backend/src/main/resources/application.properties`:

| Property                                  | Default                 | Description                                |
| ----------------------------------------- | ----------------------- | ------------------------------------------ |
| `spring.profiles.active`                  | `local`                 | Active storage strategy                    |
| `spring.servlet.multipart.max-file-size`  | `5MB`                   | Maximum size per file                      |
| `aws.s3.endpoint`                         | `http://localhost:4566` | S3 endpoint (LocalStack in dev)            |
| `aws.s3.region`                           | `us-east-1`             | S3 region                                  |
| `aws.s3.bucket`                           | `meu-bucket`            | Target bucket                              |
| `aws.s3.access-key` / `aws.s3.secret-key` | `test` / `test`         | Dummy credentials, accepted by LocalStack  |

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
- Add summarization through Spring AI with Anthropic (the dependency is commented out in `pom.xml`)
- Persist document metadata (JPA and H2 are already configured but unused)
- Make the download endpoint respect the active storage strategy instead of always reading from disk
