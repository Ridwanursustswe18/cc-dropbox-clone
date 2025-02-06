package com.example.cc_box.folder;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class FolderService {
    private final FolderRepository folderRepository;
    private final Folder folder;
    @Autowired
    public FolderService(FolderRepository folderRepository, Folder folder) {
        this.folderRepository = folderRepository;
        this.folder = folder;
    }

    public Folder createFolder(String name, String parentId, String token) throws Exception {

//        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//        String ownerId = decodedToken.getUid();
        String ownerId = token.startsWith("Bearer ") ? token.substring(7) : token;
        // Create the folder
//        Folder folder = new Folder();
        folder.setId(UUID.randomUUID().toString());
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setOwnerId(ownerId);

        // Save the folder to Firestore
        folderRepository.save(folder);
        return folder;
    }
    public List<Folder> viewFolders(String ownerId,String token) throws Exception{
//        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
//        String ownerId = decodedToken.getUid();
        return folderRepository.findByOwnerId(ownerId);
    }
}