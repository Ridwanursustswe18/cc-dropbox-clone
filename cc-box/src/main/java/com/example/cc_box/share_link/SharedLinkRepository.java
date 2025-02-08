package com.example.cc_box.share_link;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

@Repository
class SharedLinkRepository {
    private final Firestore firestore;

    public SharedLinkRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(SharedLink sharedLink) {
        firestore.collection("shared_resources").document(sharedLink.getToken()).set(sharedLink);
    }

    public SharedLink findByToken(String token) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection("shared_resources").whereEqualTo("token", token).get();
        QuerySnapshot snapshot = future.get();
        for (QueryDocumentSnapshot document : snapshot) {
            return document.toObject(SharedLink.class);
        }
        return null;
    }
}
