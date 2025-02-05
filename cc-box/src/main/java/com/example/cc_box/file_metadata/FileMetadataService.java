package com.example.cc_box.file_metadata;

import com.example.cc_box.Utils.B2Backblaze;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.concurrent.ExecutionException;

@Service
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final B2Backblaze b2Backblaze;

    @Autowired
    public FileMetadataService(
            FileMetadataRepository fileMetadataRepository, B2Backblaze b2Backblaze)
             {
                this.fileMetadataRepository = fileMetadataRepository;

                 this.b2Backblaze = b2Backblaze;
             }

    public String uploadFile(MultipartFile file, String folderPath, String ownerId, String token)
            throws IOException, InterruptedException, ExecutionException {
        String fileUrl = b2Backblaze.storeFileToB2Backblaze(file,folderPath);
        // 6. Save file metadata locally (or in your DB)
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFileName(file.getOriginalFilename());
        fileMetadata.setFileType(file.getContentType());
        fileMetadata.setFileSize(file.getSize());
        fileMetadata.setFilePath(fileUrl);
        fileMetadata.setFolderId(folderPath);
        fileMetadata.setOwnerId(ownerId);

        // Save and return metadata record id (or any identifier)
        return fileMetadataRepository.saveFileMetadata(fileMetadata);
    }
}