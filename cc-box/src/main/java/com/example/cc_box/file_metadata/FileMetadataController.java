package com.example.cc_box.file_metadata;

import com.example.cc_box.Utils.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        try {
            String token = authorizationHeader.substring(7);
            CompletableFuture<String> fileId = fileMetadataService.uploadFile(file.getBytes(), file.getOriginalFilename(), file.getContentType(), file.getSize(), folderPath,token);
            return ResponseEntity.ok(new ApiResponse(true,"File uploaded successfully with ID: " , fileId.get()));
        } catch (IOException | InterruptedException | ExecutionException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/uploadMultiple")
    public ResponseEntity<ApiResponse> uploadMultipleFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("folderPath") String folderPath,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        try {
            String token = authorizationHeader.substring(7);
            List<CompletableFuture<String>> futures = files.stream()
                    .map(file -> {
                        try {
                            byte[] fileBytes = file.getBytes();
                            String fileName = file.getOriginalFilename();
                            String fileType = file.getContentType();
                            long fileSize = file.getSize();
                            return fileMetadataService.uploadFile(
                                    fileBytes, fileName, fileType, fileSize, folderPath, token
                            );
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
                        }
                    })
                    .collect(Collectors.toList());
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            CompletableFuture<List<String>> results = allFutures.thenApply(v ->
                    futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList())
            );

            List<String> fileUrls = results.get();
            return ResponseEntity.ok(new ApiResponse(true, "Files uploaded successfully", fileUrls));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }
}

