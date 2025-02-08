package com.example.cc_box.share_link;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.PropertyName;
import com.google.cloud.firestore.annotation.ServerTimestamp;
import org.springframework.stereotype.Component;

import java.util.Date;
@Component
public class SharedLink {
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DocumentId
    private String id;
    private String token;
    private String originalUrl;
    private String ownerId;

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    private String authToken;

    public SharedLink() {

    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public SharedLink(String id,String token, String originalUrl, String ownerId, Date expiryDate, boolean publicAccess,String authToken) {
        this.id = id;
        this.token = token;
        this.originalUrl = originalUrl;
        this.ownerId = ownerId;
        this.expiryDate = expiryDate;
        this.publicAccess = publicAccess;
        this.authToken = authToken;
    }

    private Date expiryDate;
    private boolean publicAccess;


}

