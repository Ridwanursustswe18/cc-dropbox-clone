package com.example.cc_box.folder;

import com.example.cc_box.Utils.FileToMultipartFileConverter;
import com.example.cc_box.file_metadata.FileMetadata;
import com.example.cc_box.file_metadata.FileMetadataService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class FolderService {
    private final FolderRepository folderRepository;
    private final Folder folder;
    private final FileMetadataService fileMetadataService;
    @Autowired
    public FolderService(FolderRepository folderRepository, Folder folder,  FileMetadataService fileMetadataService) {
        this.folderRepository = folderRepository;
        this.folder = folder;
        this.fileMetadataService = fileMetadataService;
    }

    public Folder createFolder(String name, String parentId, String token) throws Exception {

//        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//        String ownerId = decodedToken.getUid();
        // Create the folder
//        Folder folder = new Folder();
        folder.setId(UUID.randomUUID().toString());
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setOwnerId(token);

        // Save the folder to Firestore
        folderRepository.save(folder);
        return folder;
    }
    public List<Folder> viewFolders(String ownerId,String token) throws Exception{
//        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
//        String ownerId = decodedToken.getUid();
        return folderRepository.findByOwnerId(ownerId);
    }
    public void uploadFolder(String folderPath, String parentFolderId,String token) throws ExecutionException, InterruptedException, IOException {

        File localFolder = new File(folderPath);
        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid folder path");
        }

        // Create folder metadata

        folder.setId(UUID.randomUUID().toString());
        folder.setName(folderPath);
        folder.setParentId(parentFolderId);
        folder.setOwnerId(token);
        folder.setCreatedAt(new Date());

        // Save folder metadata
        folderRepository.save(folder);

        // Process folder contents
        processFolder(localFolder, folder.getId(),token);
    }
    private void processFolder(File content, String parentFolderId, String token) throws ExecutionException, InterruptedException, IOException {
        File[] contents = content.listFiles();
        if (contents != null) {
            for (File item : contents) {
                if (item.isDirectory()) {
                    // Recursively process subfolder

                    folder.setId(UUID.randomUUID().toString());
                    folder.setName(item.getName());
                    folder.setParentId(parentFolderId);
                    folder.setOwnerId(token);
                    folder.setCreatedAt(new Date());
                    folderRepository.save(folder);
                    processFolder(item, folder.getId(),token);
                } else {
                    MultipartFile multipartFile = FileToMultipartFileConverter.convert(item);
                    fileMetadataService.uploadFile(multipartFile, parentFolderId, token);

                }
            }
        }
    }
}