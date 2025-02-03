package com.example.cc_box.Auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.google.firebase.auth.FirebaseAuthException;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public String signUp(@RequestParam String email, @RequestParam String password) {
        try {
            return authService.signUp(email, password);
        } catch (FirebaseAuthException e) {
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/verify")
    public String verifyToken(@RequestParam String idToken) {
        try {
            return authService.verifyToken(idToken);
        } catch (FirebaseAuthException e) {
            return "Error: " + e.getMessage();
        }
    }
}