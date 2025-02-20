package com.example.cc_box.Utils;

import com.example.cc_box.file_metadata.FileMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class B2Backblaze {
    private final String accountId;
    private final String applicationKey;
    private final String bucketId;
    private final String bucketName;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();

    // Instead of injecting a single FileMetadata, we inject a factory.
    private final ObjectFactory<FileMetadata> fileMetadataFactory;

    @Autowired
    public B2Backblaze(
            @Value("${ACCOUNT_ID}") String accountId,
            @Value("${APPLICATION_KEY}") String applicationKey,
            @Value("${BUCKET_ID}") String bucketId,
            @Value("${BUCKET_NAME}") String bucketName,
            ObjectFactory<FileMetadata> fileMetadataFactory) throws IOException {
        this.accountId = accountId;
        this.applicationKey = applicationKey;
        this.bucketId = bucketId;
        this.bucketName = bucketName;
        this.fileMetadataFactory = fileMetadataFactory;
    }

    @Async
    public CompletableFuture<String> storeFileToB2Backblaze(MultipartFile file, String folderPath) {
        CompletableFuture<String> future = new CompletableFuture<>();

        // 1. Authorize
        Request authRequest = new Request.Builder()
                .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
                .header("Authorization", Credentials.basic(accountId, applicationKey))
                .build();

        client.newCall(authRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response authResponse) throws IOException {
                if (!authResponse.isSuccessful()) {
                    future.completeExceptionally(new IOException("Authorization failed: " + authResponse.body().string()));
                    return;
                }

                JsonNode authJson = mapper.readTree(authResponse.body().string());
                String apiUrl = authJson.get("apiUrl").asText();
                String authToken = authJson.get("authorizationToken").asText();
                String downloadUrl = authJson.get("downloadUrl").asText();

                // 2. Get Upload URL
                String getUploadUrlEndpoint = apiUrl + "/b2api/v2/b2_get_upload_url";
                String bucketJsonPayload = "{\"bucketId\": \"" + bucketId + "\"}";
                RequestBody bucketBody = RequestBody.create(bucketJsonPayload, MediaType.parse("application/json"));
                Request getUploadUrlRequest = new Request.Builder()
                        .url(getUploadUrlEndpoint)
                        .header("Authorization", authToken)
                        .post(bucketBody)
                        .build();

                client.newCall(getUploadUrlRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        future.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response uploadUrlResponse) throws IOException {
                        if (!uploadUrlResponse.isSuccessful()) {
                            future.completeExceptionally(new IOException("b2_get_upload_url failed: " + uploadUrlResponse.body().string()));
                            return;
                        }

                        JsonNode uploadUrlJson = mapper.readTree(uploadUrlResponse.body().string());
                        String uploadUrl = uploadUrlJson.get("uploadUrl").asText();
                        String uploadAuthToken = uploadUrlJson.get("authorizationToken").asText();

                        // 3. Upload the File
                        String uniqueFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                        String filePath = folderPath + "/" + uniqueFileName;
                        String encodedFileName = URLEncoder.encode(filePath, StandardCharsets.UTF_8);

                        byte[] fileBytes = file.getBytes();
                        String sha1 = DigestUtils.sha1Hex(fileBytes);

                        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));
                        Request uploadFileRequest = new Request.Builder()
                                .url(uploadUrl)
                                .header("Authorization", uploadAuthToken)
                                .header("X-Bz-File-Name", encodedFileName)
                                .header("Content-Type", "b2/x-auto")
                                .header("Content-Length", String.valueOf(fileBytes.length))
                                .header("X-Bz-Content-Sha1", sha1)
                                .post(fileBody)
                                .build();

                        client.newCall(uploadFileRequest).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                future.completeExceptionally(e);
                            }

                            @Override
                            public void onResponse(Call call, Response uploadFileResponse) throws IOException {
                                if (!uploadFileResponse.isSuccessful()) {
                                    future.completeExceptionally(new IOException("b2_upload_file failed: " + uploadFileResponse.body().string()));
                                    return;
                                }

                                // 4. Generate Download Authorization Token
                                String downloadAuthUrl = apiUrl + "/b2api/v2/b2_get_download_authorization";
                                int validDurationInSeconds = 3600 * 24 * 7; // 7 days
                                String authPayload = "{\"bucketId\": \"" + bucketId + "\", \"fileNamePrefix\": \"" + filePath + "\", \"validDurationInSeconds\": " + validDurationInSeconds + "}";
                                RequestBody authPayloadBody = RequestBody.create(authPayload, MediaType.parse("application/json"));
                                Request downloadAuthRequest = new Request.Builder()
                                        .url(downloadAuthUrl)
                                        .header("Authorization", authToken)
                                        .post(authPayloadBody)
                                        .build();

                                client.newCall(downloadAuthRequest).enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        future.completeExceptionally(e);
                                    }

                                    @Override
                                    public void onResponse(Call call, Response downloadAuthResponse) throws IOException {
                                        if (!downloadAuthResponse.isSuccessful()) {
                                            future.completeExceptionally(new IOException("b2_get_download_authorization failed: " + downloadAuthResponse.body().string()));
                                            return;
                                        }

                                        JsonNode downloadAuthJson = mapper.readTree(downloadAuthResponse.body().string());
                                        String downloadAuthorizationToken = downloadAuthJson.get("authorizationToken").asText();

                                        // 5. Construct the File URL
                                        String fileUrl = downloadUrl + "/file/" + bucketName + "/" + filePath.replace("/", "%2F")
                                                + "?Authorization=" + downloadAuthorizationToken;
                                        future.complete(fileUrl);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        return future;
    }

    @Async
    public CompletableFuture<Map<String, FileMetadata>> listFiles(File localRoot) throws IOException {
        // Authorize account
        Request authRequest = new Request.Builder()
                .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
                .header("Authorization", Credentials.basic(accountId, applicationKey))
                .build();
        Response authResponse = client.newCall(authRequest).execute();
        if (!authResponse.isSuccessful()) {
            throw new IOException("Authorization failed: " + authResponse.body().string());
        }
        JsonNode authJson = mapper.readTree(authResponse.body().string());
        String apiUrl = authJson.get("apiUrl").asText();
        String authToken = authJson.get("authorizationToken").asText();

        // Use the local folder's name as the remote prefix
        String folderName = localRoot.getName(); // e.g. "New folder"
        if (!folderName.endsWith("/")) {
            folderName += "/";
        }

        // Call b2_list_file_names with the proper prefix
        String listFilesEndpoint = apiUrl + "/b2api/v2/b2_list_file_names";
        String payload = "{\"bucketId\": \"" + bucketId + "\", \"maxFileCount\": 1000, \"startFileName\": null, \"prefix\": \"" + folderName + "\"}";
        RequestBody payloadBody = RequestBody.create(payload, MediaType.parse("application/json"));
        Request listRequest = new Request.Builder()
                .url(listFilesEndpoint)
                .header("Authorization", authToken)
                .post(payloadBody)
                .build();
        Response listResponse = client.newCall(listRequest).execute();
        if (!listResponse.isSuccessful()) {
            throw new IOException("b2_list_file_names failed: " + listResponse.body().string());
        }
        JsonNode listJson = mapper.readTree(listResponse.body().string());

        Map<String, FileMetadata> filesMap = new HashMap<>();
        for (JsonNode fileNode : listJson.get("files")) {
            String fileName = fileNode.get("fileName").asText();
            long uploadTimestamp = fileNode.get("uploadTimestamp").asLong();
            Date createdAt = new Date(uploadTimestamp);
            // Remove the folder prefix to get the relative path
            String relativePath = fileName.substring(folderName.length());
            // Instead of instantiating with new, use the prototype bean factory
            FileMetadata metadata = fileMetadataFactory.getObject();
            metadata.setFileName(relativePath);
            metadata.setCreatedAt(createdAt);
            filesMap.put(relativePath, metadata);
        }
        return CompletableFuture.completedFuture(filesMap);
    }

    @Async
    public void downloadFile(String relativePath, File localFile) throws IOException {
        // Authorize account
        Request authRequest = new Request.Builder()
                .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
                .header("Authorization", Credentials.basic(accountId, applicationKey))
                .build();
        Response authResponse = client.newCall(authRequest).execute();
        if (!authResponse.isSuccessful()) {
            throw new IOException("Authorization failed: " + authResponse.body().string());
        }
        JsonNode authJson = mapper.readTree(authResponse.body().string());
        String apiUrl = authJson.get("apiUrl").asText();
        String authToken = authJson.get("authorizationToken").asText();
        String downloadUrl = authJson.get("downloadUrl").asText();

        // Generate Download Authorization Token for the file
        String downloadAuthUrl = apiUrl + "/b2api/v2/b2_get_download_authorization";
        int validDurationInSeconds = 3600 * 24 * 7; // 7 days
        String authPayload = "{\"bucketId\": \"" + bucketId + "\", \"fileNamePrefix\": \"" + relativePath + "\", \"validDurationInSeconds\": " + validDurationInSeconds + "}";
        RequestBody authPayloadBody = RequestBody.create(authPayload, MediaType.parse("application/json"));
        Request downloadAuthRequest = new Request.Builder()
                .url(downloadAuthUrl)
                .header("Authorization", authToken)
                .post(authPayloadBody)
                .build();
        Response downloadAuthResponse = client.newCall(downloadAuthRequest).execute();
        if (!downloadAuthResponse.isSuccessful()) {
            throw new IOException("b2_get_download_authorization failed: " + downloadAuthResponse.body().string());
        }
        JsonNode downloadAuthJson = mapper.readTree(downloadAuthResponse.body().string());
        String downloadAuthorizationToken = downloadAuthJson.get("authorizationToken").asText();

        // Construct the file download URL
        String fileUrl = downloadUrl + "/file/" + bucketName + "/" + relativePath.replace("/", "%2F")
                + "?Authorization=" + downloadAuthorizationToken;

        // Download the file
        Request downloadRequest = new Request.Builder()
                .url(fileUrl)
                .build();
        Response downloadResponse = client.newCall(downloadRequest).execute();
        if (!downloadResponse.isSuccessful()) {
            throw new IOException("Download failed: " + downloadResponse.body().string());
        }
        byte[] fileBytes = downloadResponse.body().bytes();
        if (localFile.getParentFile() != null) {
            localFile.getParentFile().mkdirs();
        }
        java.nio.file.Files.write(localFile.toPath(), fileBytes);
    }
}
