package com.document_summary_assistant.document_summary_assistant_backend.Service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
}
