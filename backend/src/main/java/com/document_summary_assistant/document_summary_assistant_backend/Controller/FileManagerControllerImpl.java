package com.document_summary_assistant.document_summary_assistant_backend.Controller;

import com.document_summary_assistant.document_summary_assistant_backend.Service.S3Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@RestController
@RequestMapping("/upload")
public class FileManagerControllerImpl implements FileManagerController{

    private final S3Service s3Service;

    public FileManagerControllerImpl(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostMapping
    @Override
    public ResponseEntity<String> saveFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No File Selected");
        }

//        try {
//            //TODO Isolate saving logic in another layer, using Strategy pattern
//            //TODO Isolate uploadDir in a static final variable
//            String uploadDir = "uploads/";
//            File directory = new File(uploadDir);
//            if (!directory.exists()) {
//                directory.mkdir();
//            }
//
//            Path path = Paths.get(uploadDir + file.getOriginalFilename());
//            Files.write(path, file.getBytes());
//            return ResponseEntity.ok("File saved at: " + path.toAbsolutePath());
//        } catch (IOException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
//        }
        try {
            return ResponseEntity.ok(s3Service.upload(file));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }

    }

    @GetMapping("/files/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        try {
            //TODO Isolate uploadDir in a static final variable
            Path filePath = Paths.get("uploads/").resolve(fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not found");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
