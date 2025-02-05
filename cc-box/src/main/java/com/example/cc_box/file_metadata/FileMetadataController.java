package com.example.cc_box.file_metadata;

import com.example.cc_box.Utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/files")
public class FileMetadataController {

    private final FileMetadataService fileMetadataService;
    @Autowired
    public FileMetadataController(FileMetadataService fileMetadataService) {
        this.fileMetadataService = fileMetadataService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("folderPath") String folderPath,
            @RequestPart("ownerId") String ownerId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        try {
            String fileId = fileMetadataService.uploadFile(file, folderPath, ownerId,authorizationHeader);
            return ResponseEntity.ok(new ApiResponse(true,"File uploaded successfully with ID: " , fileId));
        } catch (IOException | ExecutionException | InterruptedException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/uploadMultiple")
    public ResponseEntity<ApiResponse> uploadMultipleFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("folderPath") String folderPath,
            @RequestPart("ownerId") String ownerId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        try {
            List<String> fileIds = files.stream()
                    .map(file -> {
                        try {
                            return fileMetadataService.uploadFile(file, folderPath, ownerId,authorizationHeader);
                        } catch (IOException | ExecutionException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new ApiResponse(true,"Files uploaded successfully: " , fileIds));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }
}

