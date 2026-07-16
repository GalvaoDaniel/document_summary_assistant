# document-summary-assistant-backend

Spring Boot module that handles document upload and download. Storage is pluggable through a strategy chosen by Spring profile: the local filesystem, or S3 (backed by LocalStack in development).

Summarization is not implemented yet — see [Roadmap](#roadmap).

- **Group / artifact:** `com.document-summary-assistant` / `document-summary-assistant-backend`
- **Version:** `0.0.1-SNAPSHOT`
- **Packaging:** WAR (deployable to an external servlet container, or runnable standalone via the embedded Tomcat)

## Requirements

- JDK 21
- Docker, only if you intend to run the `aws` profile against LocalStack

Maven itself is not required — use the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Quick start

The default `local` profile needs no external services:

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080` and writes uploads to `./uploads/`.

To exercise the S3 path instead, start LocalStack first:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=aws
```

## Architecture

Request flow:

```
FileManagerControllerImpl  →  StorageService  →  StorageStrategy
     (REST, /upload)          (thin delegate)     ├── FileSystemStrategy  (@Profile("local"))
                                                  └── AwsS3Strategy       (@Profile("aws"))
                                                            │
                                                            └→ S3Client   (built in S3Config)
```

| Class                       | Package         | Role                                                                                                                     |
| --------------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `FileManagerController`     | `Controller`    | Interface declaring `saveFile`.                                                                                          |
| `FileManagerControllerImpl` | `Controller`    | REST endpoints under `/upload`. Handles the empty-file case and maps `IOException` to a `500`.                            |
| `StorageService`            | `Service`       | Injected with whichever `StorageStrategy` bean the active profile supplies, and delegates `upload` to it.                 |
| `StorageStrategy`           | `Service`       | Single-method interface: `String upload(MultipartFile)`.                                                                  |
| `FileSystemStrategy`        | `Service`       | `@Profile("local")`. Creates `uploads/` if needed and writes the file under its original name. Returns the absolute path. |
| `AwsS3Strategy`             | `Service`       | `@Profile("aws")`. Creates the bucket on startup (`@PostConstruct`) if absent, uploads under a `<uuid>-<name>` key, and returns that key. |
| `S3Config`                  | `Configuration` | Builds the `S3Client` bean with an endpoint override, static credentials, and `forcePathStyle(true)`.                     |
| `SummarizableDocument`      | `Model`         | Empty placeholder.                                                                                                        |
| `ServletInitializer`        | (root)          | WAR bootstrap for deployment to an external container.                                                                    |

Two details worth knowing before you change things here:

- **Exactly one `StorageStrategy` bean must exist.** `StorageService` takes a single `StorageStrategy` by constructor injection, so activating both profiles at once, or neither, fails at startup. A new strategy needs its own distinct profile.
- **`S3Config` is not profile-gated.** The `S3Client` bean is created under every profile, including `local` where nothing injects it. It's inert there, but it does mean `aws.s3.*` properties must resolve regardless of profile.

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

Returns the file as an attachment (`Content-Disposition: attachment`).

```bash
curl -O http://localhost:8080/upload/files/document.pdf
```

This endpoint resolves `fileName` against the `uploads/` directory directly rather than going through `StorageService`, so it only serves files written by the `local` profile — objects uploaded under `aws` are not reachable through it.

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

On startup `AwsS3Strategy.initBucket()` creates `meu-bucket` if it isn't there yet.

**3. Upload a file.** The response body is the object key, in the form `<uuid>-<original-filename>`:

```bash
curl -F "file=@document.pdf" http://localhost:8080/upload
# → 3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf
```

**4. Check the object inside the LocalStack container.** The image ships with `awslocal`, a wrapper that targets the local endpoint for you:

```bash
docker compose exec localstack awslocal s3 ls s3://meu-bucket/
```

Use `docker compose exec` rather than `docker exec <name>`: it resolves the `localstack` service by name, so it keeps working regardless of the generated container name (`backend-localstack-1`, unless the compose project name is overridden).

For object metadata — size, content type, last modified — query the key returned in step 3:

```bash
docker compose exec localstack awslocal s3api head-object \
  --bucket meu-bucket --key 3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf
```

And to pull the object back out and diff it against the original, confirming the bytes survived the round trip:

```bash
docker compose exec localstack awslocal s3 cp \
  s3://meu-bucket/3f2a1c7e-9b0d-4e5a-8c31-6d7f2e4a1b90-document.pdf /tmp/roundtrip.pdf
docker compose cp localstack:/tmp/roundtrip.pdf ./roundtrip.pdf
diff document.pdf roundtrip.pdf && echo "identical"
```

If you have the AWS CLI on the host, the same checks work without entering the container by pointing it at the LocalStack endpoint:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://meu-bucket/
```

**Note:** `GET /upload/files/{fileName}` will *not* retrieve these objects — it reads from the local `uploads/` directory, so under the `aws` profile the `awslocal` commands above are the way to verify an upload. Also keep in mind that LocalStack's S3 data is in-memory here, since no volume is mounted for it in `docker-compose.yml`; `docker compose down` discards every uploaded object.

## Configuration

`src/main/resources/application.properties`:

| Property                                     | Default                 | Notes                                                    |
| -------------------------------------------- | ----------------------- | -------------------------------------------------------- |
| `spring.application.name`                    | `document-summary-assistant-backend` |                                             |
| `spring.profiles.active`                     | `local`                 | Selects the storage strategy: `local` or `aws`            |
| `spring.servlet.multipart.max-file-size`     | `5MB`                   | Per-file limit                                            |
| `spring.servlet.multipart.max-request-size`  | `5MB`                   | Whole-request limit                                       |
| `spring.cloud.aws.region.static`             | `us-east-1`             | Region for the Spring Cloud AWS autoconfiguration         |
| `aws.s3.endpoint`                            | `http://localhost:4566` | LocalStack in dev; drop the override for real AWS         |
| `aws.s3.region`                              | `us-east-1`             | Region used by the `S3Client` bean                        |
| `aws.s3.bucket`                              | `meu-bucket`            | Created on startup under the `aws` profile if missing     |
| `aws.s3.access-key` / `aws.s3.secret-key`    | `test` / `test`         | Dummy values; LocalStack does not validate credentials    |

The committed credentials are placeholders for LocalStack. For real AWS, supply them through environment variables or another external source rather than editing them into this file.

## Build and test

```bash
./mvnw clean install                 # full build
./mvnw test                          # all tests
./mvnw test -Dtest=DocumentSummaryAssistantBackendApplicationTests   # single class
./mvnw clean package                 # WAR into target/
```

Test coverage is currently limited to the application context-load check in `DocumentSummaryAssistantBackendApplicationTests`.

Lombok is wired in as an annotation processor via `maven-compiler-plugin` and excluded from the packaged artifact by `spring-boot-maven-plugin`. If your IDE reports missing generated methods, enable annotation processing in it.

## Dependencies of note

- `spring-cloud-aws-starter-s3` (4.0.0) — also autoconfigures S3 beans, but the explicit `S3Client` in `S3Config` takes precedence.
- `spring-boot-starter-data-jpa` + H2 (runtime) — configured but not yet used; no entities or repositories exist so far.
- `spring-ai-starter-model-anthropic` — commented out in `pom.xml`, along with the `spring-ai-bom` import in `dependencyManagement`. Both need uncommenting together when summarization work begins.

## Roadmap

- Flesh out `SummarizableDocument`, currently an empty class
- Add summarization via Spring AI with Anthropic
- Persist document metadata using the already-configured JPA/H2 setup
- Route the download endpoint through `StorageService` so it works under the `aws` profile
- Lift the hardcoded `uploads/` path out of `FileManagerControllerImpl` (there's a `TODO` on it) and share it with `FileSystemStrategy`
