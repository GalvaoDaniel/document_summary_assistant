package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Profile("local")
@Service
public class FileSystemStrategy implements StorageStrategy {

    private static final String UPLOADDIR = "uploads/";

    @Override
    public String upload(MultipartFile file) throws IOException {
        File directory = new File(UPLOADDIR);
        if (!directory.exists()) {
            directory.mkdir();
        }

        Path path = Paths.get(UPLOADDIR + file.getOriginalFilename());
        Files.write(path, file.getBytes());
        return "File saved at: " + path.toAbsolutePath();
    }

    @Override
    public Resource serveFile(String filename) {
        Path filePath = Paths.get(UPLOADDIR).resolve(filename);
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("File not found");
        }

        return resource;
    }
}
