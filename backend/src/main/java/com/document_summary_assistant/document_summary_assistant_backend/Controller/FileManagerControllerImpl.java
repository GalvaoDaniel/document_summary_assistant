package com.document_summary_assistant.document_summary_assistant_backend.Controller;

import com.document_summary_assistant.document_summary_assistant_backend.Service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("/upload")
public class FileManagerControllerImpl implements FileManagerController{

    private final StorageService storageService;

    public FileManagerControllerImpl(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @Override
    public ResponseEntity<String> saveFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No File Selected");
        }

        try {
            return ResponseEntity.ok(storageService.upload(file));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }

    }

    @GetMapping("/files/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {

        Resource resource = storageService.serveFile(fileName);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
