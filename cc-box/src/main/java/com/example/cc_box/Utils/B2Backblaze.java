package com.example.cc_box.Utils;

import com.example.cc_box.file_metadata.FileMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class B2Backblaze implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(B2Backblaze.class);
    private static final String B2_API_BASE_URL = "https://api.backblazeb2.com/b2api/v2";
    private static final Duration DEFAULT_AUTH_TOKEN_VALIDITY = Duration.ofHours(24);
    private static final int MAX_RETRIES = 3;

    private final String accountId;
    private final String applicationKey;
    private final String bucketId;
    private final String bucketName;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final ObjectFactory<FileMetadata> fileMetadataFactory;

    private volatile B2AuthData currentAuth;
    private final Object authLock = new Object();

    private static class B2AuthData {
        final String authToken;
        final String apiUrl;
        final String downloadUrl;
        final long expiryTime;

        B2AuthData(String authToken, String apiUrl, String downloadUrl, long expiryTime) {
            this.authToken = authToken;
            this.apiUrl = apiUrl;
            this.downloadUrl = downloadUrl;
            this.expiryTime = expiryTime;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    @Autowired
    public B2Backblaze(
            @Value("${account.id}") String accountId,
            @Value("${application.key}") String applicationKey,
            @Value("${bucket.id}") String bucketId,
            @Value("${bucket.name}") String bucketName,
            ObjectFactory<FileMetadata> fileMetadataFactory) {
        this.accountId = accountId;
        this.applicationKey = applicationKey;
        this.bucketId = bucketId;
        this.bucketName = bucketName;
        this.fileMetadataFactory = fileMetadataFactory;

        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    private CompletableFuture<B2AuthData> authenticate() {
        synchronized (authLock) {
            if (currentAuth != null && currentAuth.isValid()) {
                return CompletableFuture.completedFuture(currentAuth);
            }

            Request authRequest = new Request.Builder()
                    .url(B2_API_BASE_URL + "/b2_authorize_account")
                    .header("Authorization", Credentials.basic(accountId, applicationKey))
                    .build();

            return makeAsyncCall(authRequest)
                    .thenApply(response -> {
                        try {
                            JsonNode authJson = mapper.readTree(response);
                            long expiryTime = System.currentTimeMillis() + DEFAULT_AUTH_TOKEN_VALIDITY.toMillis();

                            currentAuth = new B2AuthData(
                                    authJson.get("authorizationToken").asText(),
                                    authJson.get("apiUrl").asText(),
                                    authJson.get("downloadUrl").asText(),
                                    expiryTime
                            );
                            return currentAuth;
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to parse auth response", e);
                        }
                    });
        }
    }

    @Async
    public CompletableFuture<String> storeFileToB2Backblaze(MultipartFile file, String folderPath) {
        return authenticate()
                .thenCompose(auth -> {
                    try {
                        return getUploadUrl(auth);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenCompose(uploadData -> uploadFile(uploadData, file, folderPath))
                .thenCompose(this::generateDownloadUrl);
    }

    private CompletableFuture<JsonNode> getUploadUrl(B2AuthData auth) throws JsonProcessingException {
        ObjectNode payload = mapper.createObjectNode().put("bucketId", bucketId);
        Request request = new Request.Builder()
                .url(auth.apiUrl + "/b2api/v2/b2_get_upload_url")
                .header("Authorization", auth.authToken)
                .post(RequestBody.create(mapper.writeValueAsString(payload), MediaType.parse("application/json")))
                .build();

        return makeAsyncCall(request)
                .thenApply(response -> {
                    try {
                        return mapper.readTree(response);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to parse upload URL response", e);
                    }
                });
    }

    private CompletableFuture<String> uploadFile(JsonNode uploadData, MultipartFile file, String folderPath) {
        try {
            String uploadUrl = uploadData.get("uploadUrl").asText();
            String uploadAuthToken = uploadData.get("authorizationToken").asText();

            File folder = new File(folderPath);
            String filePath = folder.getName() + "/" + Objects.requireNonNull(file.getOriginalFilename()).replaceFirst("safe_\\d+", "");
            String encodedFileName = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            byte[] fileBytes = file.getBytes();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", uploadAuthToken)
                    .header("X-Bz-File-Name", encodedFileName)
                    .header("Content-Type", "b2/x-auto")
                    .header("X-Bz-Content-Sha1", DigestUtils.sha1Hex(fileBytes))
                    .post(RequestBody.create(fileBytes, MediaType.parse("application/octet-stream")))
                    .build();

            return makeAsyncCall(request);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> generateDownloadUrl(String uploadResponse) {
        return authenticate()
                .thenCompose(auth -> {
                    try {
                        JsonNode uploadJson = mapper.readTree(uploadResponse);
                        String fileName = uploadJson.get("fileName").asText();

                        ObjectNode authPayload = mapper.createObjectNode()
                                .put("bucketId", bucketId)
                                .put("fileNamePrefix", fileName)
                                .put("validDurationInSeconds", 7 * 24 * 3600);

                        Request request = new Request.Builder()
                                .url(auth.apiUrl + "/b2api/v2/b2_get_download_authorization")
                                .header("Authorization", auth.authToken)
                                .post(RequestBody.create(mapper.writeValueAsString(authPayload),
                                        MediaType.parse("application/json")))
                                .build();

                        return makeAsyncCall(request)
                                .thenApply(response -> {
                                    try {
                                        JsonNode authJson = mapper.readTree(response);
                                        String downloadAuthToken = authJson.get("authorizationToken").asText();
                                        return auth.downloadUrl + "/file/" + bucketName + "/" +
                                                URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                                                        .replace("+", "%20") + "?Authorization=" + downloadAuthToken;
                                    } catch (IOException e) {
                                        throw new RuntimeException("Failed to generate download URL", e);
                                    }
                                });
                    } catch (IOException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    @Async
    public CompletableFuture<Map<String, FileMetadata>> listFiles(String folderId) {
        return authenticate()
                .thenCompose(auth -> {
                    ObjectNode payload = mapper.createObjectNode()
                            .put("bucketId", bucketId)
                            .put("maxFileCount", 1000)
                            .putNull("startFileName")
                            .put("prefix", folderId.endsWith("/") ? folderId : folderId + "/");

                    Request request = null;
                    try {
                        request = new Request.Builder()
                                .url(auth.apiUrl + "/b2api/v2/b2_list_file_names")
                                .header("Authorization", auth.authToken)
                                .post(RequestBody.create(mapper.writeValueAsString(payload),
                                        MediaType.parse("application/json")))
                                .build();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    return makeAsyncCall(request)
                            .thenApply(response -> {
                                try {
                                    Map<String, FileMetadata> filesMap = new HashMap<>();
                                    JsonNode listJson = mapper.readTree(response);

                                    for (JsonNode fileNode : listJson.get("files")) {
                                        String remoteKey = fileNode.get("fileName").asText();
                                        String relativePath = remoteKey.substring(
                                                remoteKey.startsWith(folderId) ? folderId.length() : 0);

                                        FileMetadata metadata = fileMetadataFactory.getObject();
                                        metadata.setFileName(relativePath);
                                        metadata.setCreatedAt(new Date(fileNode.get("uploadTimestamp").asLong()));
                                        filesMap.put(relativePath, metadata);
                                    }
                                    return filesMap;
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to parse file list", e);
                                }
                            });
                });
    }

    @Async
    public CompletableFuture<Void> downloadFile(String relativePath, File localFile) {
        return authenticate()
                .thenCompose(auth -> {
                    // Remove leading slash if present
                    String normalizedPath = relativePath.startsWith("/")
                            ? relativePath.substring(1)
                            : relativePath;

                    logger.debug("Downloading file with normalized path: {}", normalizedPath);

                    ObjectNode payload = mapper.createObjectNode()
                            .put("bucketId", bucketId)
                            .put("fileNamePrefix", normalizedPath)
                            .put("validDurationInSeconds", 7 * 24 * 3600);

                    Request request;
                    try {
                        request = new Request.Builder()
                                .url(auth.apiUrl + "/b2api/v2/b2_get_download_authorization")
                                .header("Authorization", auth.authToken)
                                .post(RequestBody.create(
                                        mapper.writeValueAsString(payload),
                                        MediaType.parse("application/json")))
                                .build();
                    } catch (JsonProcessingException e) {
                        logger.error("Failed to create authorization request", e);
                        return CompletableFuture.failedFuture(e);
                    }

                    return makeAsyncCall(request)
                            .thenCompose(response -> {
                                try {
                                    String downloadAuthToken = mapper.readTree(response)
                                            .get("authorizationToken").asText();

                                    // Split the path into folder ID and file name
                                    System.out.println(normalizedPath);
                                    int slashIndex = normalizedPath.indexOf('/');
                                    if (slashIndex == -1) {
                                        throw new IOException("Invalid relativePath: missing folder ID or file name");
                                    }

                                    String folderId = normalizedPath.substring(0, slashIndex); // Folder ID (do not encode)
                                    String fileName = normalizedPath.substring(slashIndex + 1); // File name (encode)

                                    // Encode only the file name
                                    String encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                                            .replace("+", "%20");

                                    // Construct the full path with folder ID and encoded file name
                                    String fullPath = folderId + "/" + encodedFileName;

                                    String fileUrl = String.format("%s/file/%s/%s?Authorization=%s",
                                            auth.downloadUrl,
                                            bucketName,
                                            fullPath,
                                            downloadAuthToken);

                                    logger.debug("Attempting to download from URL: {}", fileUrl);

                                    Request downloadRequest = new Request.Builder()
                                            .url(fileUrl)
                                            .build();

                                    return downloadBinaryFile(downloadRequest, localFile);
                                } catch (IOException e) {
                                    logger.error("Failed to process authorization response", e);
                                    return CompletableFuture.failedFuture(e);
                                }
                            });
                });
    }
    /**
     * Downloads a binary file from the given request and saves it to the specified local file
     */
    private CompletableFuture<Void> downloadBinaryFile(Request request, File localFile) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Make sure parent directories exist
        localFile.getParentFile().mkdirs();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("Download request failed", e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = body != null ? body.string() : "No error body";
                        String errorMessage = "Download failed: " + response.code() + " - " + errorBody;
                        logger.error(errorMessage);
                        future.completeExceptionally(new IOException(errorMessage));
                        return;
                    }

                    if (body == null) {
                        future.completeExceptionally(new IOException("Empty response body"));
                        return;
                    }

                    // Stream the binary data directly to the file
                    try (InputStream inputStream = body.byteStream();
                         OutputStream outputStream = new FileOutputStream(localFile)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        logger.info("Successfully downloaded file to: {}", localFile);
                        future.complete(null);
                    } catch (IOException e) {
                        logger.error("Failed to save file: {}", localFile, e);
                        future.completeExceptionally(e);
                    }
                }
            }
        });

        return future;
    }

    // Keep the existing makeAsyncCall method for non-binary responses
    private CompletableFuture<String> makeAsyncCall(Request request) {
        CompletableFuture<String> future = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = body != null ? body.string() : "No error body";
                        logger.error("B2 API request failed: {} - {}", response.code(), errorBody);
                        future.completeExceptionally(
                                new IOException("Request failed: " + response.code() + " - " + errorBody));
                        return;
                    }
                    future.complete(body != null ? body.string() : "");
                }
            }
        });
        return future;
    }
    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}