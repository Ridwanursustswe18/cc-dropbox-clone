package com.example.cc_box.share_link;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


@Service
public class SharedLinkService {
    private final SharedLinkRepository sharedLinkRepository;
    private final SharedLink link;
    @Autowired
    public SharedLinkService(SharedLinkRepository sharedLinkRepository, SharedLink link) {
        this.sharedLinkRepository = sharedLinkRepository;
        this.link = link;
    }
    public SharedLink createSharedLink(String originalUrl, String loginToken) {
        // Generate a unique token
        String ownerId  = loginToken.startsWith("Bearer ") ? loginToken.substring(7) : loginToken;
        //FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//        String ownerId = decodedToken.getUid();

        String token = UUID.randomUUID().toString().substring(0, 8);
        link.setToken(token);
        link.setOriginalUrl(originalUrl);
        link.setOwnerId(ownerId);
        link.setExpiryDate(calculateExpiryDate()); // Optional
        link.setAuthToken(loginToken);
        sharedLinkRepository.save(link);

        return link;
    }
    public SharedLink getResourceByToken(String token) throws ExecutionException, InterruptedException {
        return sharedLinkRepository.findByToken(token);
    }
    private Date calculateExpiryDate() {
        // Set expiry date to 7 days from now
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 30);
        return calendar.getTime();
    }

}
