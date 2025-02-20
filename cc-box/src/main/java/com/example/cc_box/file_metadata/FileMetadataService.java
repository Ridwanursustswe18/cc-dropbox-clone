package com.example.cc_box.file_metadata;

import com.example.cc_box.Utils.B2Backblaze;
import com.example.cc_box.Utils.FileToMultipartFileConverter;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

@Service
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final B2Backblaze b2Backblaze;
    private final FileMetadata fileMetadata;

    @Autowired
    public FileMetadataService(
            FileMetadataRepository fileMetadataRepository, B2Backblaze b2Backblaze, FileMetadata fileMetadata)
             {
                this.fileMetadataRepository = fileMetadataRepository;
                 this.b2Backblaze = b2Backblaze;
                 this.fileMetadata = fileMetadata;
             }

    public CompletableFuture<String> uploadFile(MultipartFile file, String folderPath, String token) throws IOException {
        return b2Backblaze.storeFileToB2Backblaze(copyMultipartFile(file), folderPath)
                .thenApply(url -> {
                    fileMetadata.setFileName(file.getOriginalFilename());
                    fileMetadata.setFileType(file.getContentType());
                    fileMetadata.setFileSize(file.getSize());
                    fileMetadata.setFilePath(url);
                    fileMetadata.setFolderId(folderPath);
                    fileMetadata.setOwnerId(token);
                    return url;
                })
                .thenCompose(url -> CompletableFuture.supplyAsync(() ->
                {
                    try {
                        return fileMetadataRepository.saveFileMetadata(fileMetadata);
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    throw new CompletionException("Failed to upload file to B2", ex);
                });
    }
    public MultipartFile copyMultipartFile(MultipartFile multipartFile) throws IOException {
        File safeFile = File.createTempFile("safe_", multipartFile.getOriginalFilename());
        multipartFile.transferTo(safeFile);
        return FileToMultipartFileConverter.convert(safeFile);
    }

    }