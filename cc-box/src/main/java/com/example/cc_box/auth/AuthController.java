package com.example.cc_box.auth;

import com.example.cc_box.Utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.firebase.auth.FirebaseAuthException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signUp(
            @RequestParam String email,
            @RequestParam String password
    ) {
        try {
            String result = authService.signUp(email, password);
            return ResponseEntity.ok(new ApiResponse(true, "User created successfully", result));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse> verifyToken(@RequestParam String idToken) {
        try {
            String result = authService.verifyToken(idToken);
            return ResponseEntity.ok(new ApiResponse(true, "Token verified successfully", result));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        }
    }
}