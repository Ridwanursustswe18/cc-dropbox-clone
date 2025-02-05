package com.example.cc_box.folder;

import com.example.cc_box.Utils.ApiResponse;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

            Folder result =  folderService.createFolder(name, parentId, authorizationHeader);
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

            List<Folder> result =  folderService.viewFolders(ownerId, authorizationHeader);
            return ResponseEntity.ok(new ApiResponse(true, "Folders fetched successfully", result));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage(), null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}