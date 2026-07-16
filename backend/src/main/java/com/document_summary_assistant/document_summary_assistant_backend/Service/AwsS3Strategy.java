package com.document_summary_assistant.document_summary_assistant_backend.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Profile("aws")
@Service
public class AwsS3Strategy implements StorageStrategy {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public AwsS3Strategy(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @PostConstruct
    public void initBucket() {
        boolean exists = s3Client.listBuckets().buckets().stream()
                .anyMatch(b -> b.name().equals(bucket));
        if (!exists) {
            s3Client.createBucket(b -> b.bucket(bucket));
        }
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        String key = UUID.randomUUID() + "-" + file.getOriginalFilename();
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes())
        );
        return key;
    }

    private ResponseInputStream<GetObjectResponse> download(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    @Override
    public Resource serveFile(String fileName) {
        ResponseInputStream<GetObjectResponse> s3Object = download(fileName);

        return new InputStreamResource(s3Object);
    }
}
