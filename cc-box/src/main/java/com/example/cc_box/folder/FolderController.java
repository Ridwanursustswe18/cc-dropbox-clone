package com.example.cc_box.folder;

import com.example.cc_box.Utils.ApiResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    @Autowired
    private FolderService folderService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createFolder(
            @RequestParam String name,
            @RequestParam(required = false) String parentId,
            @RequestHeader("Authorization") String authorizationHeader
    ){
        try {
            String token = authorizationHeader.substring(7);
            Folder result =  folderService.createFolder(name, parentId, token);
            return ResponseEntity.ok(new ApiResponse(true, "Folder created successfully", result));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    @GetMapping("/{ownerId}")
    public ResponseEntity<ApiResponse> viewFolders(@PathVariable String ownerId,@RequestHeader("Authorization") String authorizationHeader){
        try {
            String token = authorizationHeader.substring(7);
            List<Folder> result =  folderService.viewFolders(ownerId, token);
            return ResponseEntity.ok(new ApiResponse(true, "Folders fetched successfully", result));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFolder(
            @RequestParam("folderPath") String folderPath,
            @RequestParam(value = "parentFolderId", required = false) String parentFolderId,
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            String token = authorizationHeader.substring(7);

            folderService.uploadFolder(folderPath,parentFolderId,token);

            return ResponseEntity.ok(Map.of(
                    "message", "Folder uploaded successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload folder: " + e.getMessage()));
        }
    }
}