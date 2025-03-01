package com.example.cc_box.folder_sync;
import com.example.cc_box.Utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("api/v1/sync")
public class SyncController {
    private final TwoWaySyncTool syncTool;
    @Autowired
    public SyncController(TwoWaySyncTool syncTool) {
        this.syncTool = syncTool;
    }

    @PostMapping("/set-folder")
    public ResponseEntity<ApiResponse> setFolder(@RequestParam("path") String path,@RequestHeader("Authorization") String authorizationToken) throws ExecutionException, InterruptedException, IOException {
        String token = authorizationToken.startsWith("Bearer ") ? authorizationToken.substring(7) : authorizationToken;

        syncTool.addLocalRootPath(path,token);
        return ResponseEntity.ok(new ApiResponse(true,"Folder added for syncing: " , path));
    }
}
