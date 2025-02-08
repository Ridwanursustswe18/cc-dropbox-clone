package com.example.cc_box.share_link;

import com.example.cc_box.Utils.ApiResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/share")
public class SharedLinkController {

    private final SharedLinkService sharedLinkService;

    public SharedLinkController(SharedLinkService sharedLinkService) {
        this.sharedLinkService = sharedLinkService;
    }


    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createShareLink(@RequestBody CreateShareRequest request,
                                                       @RequestHeader("Authorization") String token) {
        try {
            // Verify Firebase token
//            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(
//                    token.replace("Bearer ", "")
//            );

            SharedLink resource = sharedLinkService.createSharedLink(request.url(),token);

            // Generate the shareable link
            String shareableLink = "http://localhost:8080/share/" + resource.getToken();

            return ResponseEntity.ok(new ApiResponse(true,"link generated successfully",Map.of(
                    "shareLink", shareableLink,
                    "expiryDate", resource.getExpiryDate()
            )));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }
}

record CreateShareRequest(String url) {}
