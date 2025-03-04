package com.example.cc_box.folder_sync;

import com.example.cc_box.Utils.B2Backblaze;
import com.example.cc_box.Utils.FileToMultipartFileConverter;
import com.example.cc_box.file_metadata.FileMetadata;
import com.example.cc_box.file_metadata.FileMetadataService;
import com.example.cc_box.folder.FolderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Component
public class TwoWaySyncTool implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(TwoWaySyncTool.class);

    private final B2Backblaze client;
    private final Map<String, SyncConfig> syncConfigs = new ConcurrentHashMap<>();
    private final FolderService folderService;
    private final FileMetadataService fileMetadataService;
    private final Map<String, Long> lastModifiedCache = new ConcurrentHashMap<>();

    private static class SyncConfig {
        final String folderId;
        final String authToken;
        final Path localRoot;

        SyncConfig(String folderId, String authToken, Path localRoot) {
            this.folderId = folderId;
            this.authToken = authToken;
            this.localRoot = localRoot;
        }
    }

    @Autowired
    public TwoWaySyncTool(B2Backblaze client, FolderService folderService, FileMetadataService fileMetadataService) {
        this.client = client;
        this.folderService = folderService;
        this.fileMetadataService = fileMetadataService;
    }

    public void addLocalRootPath(String localRootPath, String token) throws ExecutionException, InterruptedException, IOException {
        Path localRoot = Paths.get(localRootPath).toAbsolutePath().normalize();

        if (!Files.isDirectory(localRoot)) {
            throw new IllegalArgumentException("Local root must be an existing directory: " + localRootPath);
        }

        String folderId = folderService.uploadFolder(localRootPath, null, token);
        syncConfigs.put(localRootPath, new SyncConfig(folderId, token, localRoot));
        logger.info("Added folder for syncing: {}", localRootPath);

        // Initialize the last modified cache for existing files
        try {
            Files.walk(localRoot)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String relativePath = localRoot.relativize(path).toString();
                            lastModifiedCache.put(relativePath, Files.getLastModifiedTime(path).toMillis());
                        } catch (IOException e) {
                            logger.error("Error reading file timestamp: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error initializing last modified cache", e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Running initial sync...");
        syncAllFolders();
    }

    @Scheduled(fixedRateString = "${sync.poll-interval:60000}")
    public void syncAllFolders() {
        if (syncConfigs.isEmpty()) {
            logger.info("No folders set for sync. Skipping...");
            return;
        }

        logger.info("Starting scheduled sync...");
        syncConfigs.forEach((path, config) -> {
            try {
                sync(config);
            } catch (Exception e) {
                logger.error("Error syncing folder: {}", path, e);
            }
        });
        logger.info("Scheduled sync completed.");
    }

    private void sync(SyncConfig config) throws IOException {
        logger.info("Syncing folder: {}", config.localRoot);
        Map<String, Path> localFiles = listLocalFiles(config.localRoot);

        client.listFiles(config.folderId)
                .thenCompose(remoteFiles -> {
                    List<CompletableFuture<Void>> tasks = new ArrayList<>();
                    logger.debug("Remote files found: {} files", remoteFiles.size());
                    remoteFiles.forEach((remotePath, remoteMeta) -> {
                        String normalizedRemotePath = remotePath.startsWith("/") ?
                                remotePath.substring(1) : remotePath;
                        boolean existsLocally = localFiles.keySet().stream()
                                .map(path -> path.startsWith("/") ? path.substring(1) : path)
                                .anyMatch(normalizedPath -> normalizedPath.equals(normalizedRemotePath));

                        if (!existsLocally) {
                            Path targetPath = config.localRoot.resolve(normalizedRemotePath);
                            logger.info("Downloading remote file: '{}' to '{}'", normalizedRemotePath, targetPath);
                            downloadFile(normalizedRemotePath, targetPath.toFile(), config, tasks);
                        }
                    });
                    localFiles.forEach((relativePath, localFile) -> {
                        try {
                            handleLocalFile(relativePath, localFile, remoteFiles, config, tasks);
                        } catch (IOException e) {
                            logger.error("Error handling local file: " + relativePath, e);
                        }
                    });

                    return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                            .thenRun(() -> logger.info("Sync complete for: {}", config.localRoot));
                });
    }

    private void handleLocalFile(String relativePath, Path localFile, Map<String, FileMetadata> remoteFiles,
                                 SyncConfig config, List<CompletableFuture<Void>> tasks) throws IOException {
        FileMetadata remoteMeta = remoteFiles.get(relativePath);
        long currentLastModified = Files.getLastModifiedTime(localFile).toMillis();
        Long cachedLastModified = lastModifiedCache.get(relativePath);
        if (cachedLastModified != null && currentLastModified == cachedLastModified) {
            logger.debug("File unchanged since last sync, skipping: {}", relativePath);
            return;
        }

        if (remoteMeta == null) {
            logger.info("Uploading new local file: {}", relativePath);
            uploadFile(localFile, config, tasks);
            lastModifiedCache.put(relativePath, currentLastModified);
        }
//        else {
//            Date remoteCreatedAt = remoteMeta.getCreatedAt();
//             if (remoteCreatedAt.getTime() > currentLastModified) {
//                String fullRemotePath = config.folderId + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
//                logger.info("Downloading newer remote file: {}", fullRemotePath);
//                downloadFile(fullRemotePath, localFile.toFile(), config, tasks);
//
//            }
//        }
    }

    private void uploadFile(Path localFile, SyncConfig config, List<CompletableFuture<Void>> tasks) throws IOException {
        MultipartFile multipartFile = FileToMultipartFileConverter.convert(localFile.toFile());
        tasks.add(CompletableFuture.runAsync(() -> {
            try {
                fileMetadataService.uploadFile(multipartFile.getBytes(),multipartFile.getOriginalFilename(),multipartFile.getContentType(), multipartFile.getSize(), config.folderId, config.authToken);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file: " + localFile, e);
            }
        }));
    }

    private void downloadFile(String remotePath, File localFile, SyncConfig config, List<CompletableFuture<Void>> tasks) {
        // Ensure parent directories exist
        if (localFile.getParentFile() != null) {
            localFile.getParentFile().mkdirs();
        }

        logger.info("Starting download of '{}' to '{}'", remotePath, localFile.getAbsolutePath());
        String fullPath = config.folderId + "/" + remotePath;
        String normalizedPath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        CompletableFuture<Void> downloadTask = client.downloadFile(normalizedPath, localFile)
                .thenRun(() -> {
                    logger.info("Successfully downloaded: '{}' to '{}'", normalizedPath, localFile.getAbsolutePath());
                })
                .exceptionally(ex -> {
                    logger.error("Download failed for '{}': {}", normalizedPath, ex.getMessage());
                    return null;
                });

        tasks.add(downloadTask);
    }
//    private void handleRemoteOnlyFile(String relativePath, SyncConfig config, List<CompletableFuture<Void>> tasks) {
//        // Simply resolve the file in the specified directory
//        Path localFile = config.localRoot.resolve(relativePath);
//        logger.info("Downloading file: {} to: {}", relativePath, localFile);
//        downloadFile(relativePath, localFile.toFile(),  tasks);
//    }
    private Map<String, Path> listLocalFiles(Path root) throws IOException {
        Map<String, Path> filesMap = new HashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String relativePath = root.relativize(file).toString().replace('\\', '/');
                filesMap.put(relativePath, file);
                return FileVisitResult.CONTINUE;
            }
        });
        logger.debug("Local files found: {}", filesMap.keySet());
        return filesMap;
    }
}