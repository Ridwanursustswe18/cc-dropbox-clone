package com.example.cc_box.file_metadata;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

@Repository
public class FileMetadataRepository {

    private final Firestore firestore;
    @Autowired
    public FileMetadataRepository() {
        firestore = FirestoreClient.getFirestore();
    }

    public String saveFileMetadata(FileMetadata fileMetadata) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection("files").document();
        fileMetadata.setId(docRef.getId());
        WriteResult writeResult = docRef.set(fileMetadata).get();
        return fileMetadata.getId();
    }
}

