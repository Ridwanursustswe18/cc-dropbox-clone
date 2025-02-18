package com.example.cc_box.file_metadata;

import com.example.cc_box.Utils.B2Backblaze;
import com.example.cc_box.Utils.FileToMultipartFileConverter;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

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

    public String uploadFile(MultipartFile file, String folderPath, String token)
            throws IOException, InterruptedException, ExecutionException {
       // FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//        String ownerId = decodedToken.getUid();

        b2Backblaze.storeFileToB2Backblaze(copyMultipartFile(file), folderPath)
                .thenAccept(fileUrl -> {
                    // This will block until the result is available
                    fileMetadata.setFileName(file.getOriginalFilename());
                    fileMetadata.setFileType(file.getContentType());
                    fileMetadata.setFileSize(file.getSize());
                    fileMetadata.setFilePath(fileUrl);
                    fileMetadata.setFolderId(folderPath);
                    fileMetadata.setOwnerId(token);
                })
                .exceptionally(ex -> {

                    ex.printStackTrace();
                    return null;
                });





        // 6. Save file metadata locally (or in your DB)
//      FileMetadata fileMetadata = new FileMetadata();


        // Save and return metadata record id (or any identifier)
        return fileMetadataRepository.saveFileMetadata(fileMetadata);
    }
    public MultipartFile copyMultipartFile(MultipartFile multipartFile) throws IOException {
        File safeFile = File.createTempFile("safe_", multipartFile.getOriginalFilename());
        multipartFile.transferTo(safeFile);
        return FileToMultipartFileConverter.convert(safeFile);
    }

}