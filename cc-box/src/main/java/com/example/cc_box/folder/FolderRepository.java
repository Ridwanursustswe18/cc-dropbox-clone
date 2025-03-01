package com.example.cc_box.folder;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class FolderRepository {

    private final Firestore firestore;

    @Autowired
    public FolderRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Save a folder to Firestore
    public void save(Folder folder) throws ExecutionException, InterruptedException {
        firestore.collection("folders").document(folder.getId()).set(folder).get();
    }

    // Find folders by owner ID
    public List<Folder> findByOwnerId(String ownerId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection("folders").whereEqualTo("ownerId", ownerId);
        return query.get().get().getDocuments().stream()
                .map(document -> document.toObject(Folder.class))
                .collect(Collectors.toList());
    }

    // Find subfolders by parent ID
    public List<Folder> findByParentId(String parentId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection("folders").whereEqualTo("parentId", parentId);
        return query.get().get().getDocuments().stream()
                .map(document -> document.toObject(Folder.class))
                .collect(Collectors.toList());
    }
    // Find a folder by name and parent ID
    public Folder findByNameAndParentId(String folderName, String parentId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection("folders")
                .whereEqualTo("name", folderName)
                .whereEqualTo("parentId", parentId);

        List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();

        if (documents.isEmpty()) {
            return null; // Folder not found
        }

        return documents.getFirst().toObject(Folder.class); // Return the first match
    }

}