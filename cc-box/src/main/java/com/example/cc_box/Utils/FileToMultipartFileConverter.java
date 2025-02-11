package com.example.cc_box.Utils;// First, create a utility class to handle the conversion
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileToMultipartFileConverter {
    public static MultipartFile convert(File file) throws IOException {
        return new MultipartFile() {
            @Override
            public String getName() {
                return file.getName();
            }

            @Override
            public String getOriginalFilename() {
                return file.getName();
            }

            @Override
            public String getContentType() {
                // You might want to detect the content type properly
                return "application/octet-stream";
            }

            @Override
            public boolean isEmpty() {
                return file.length() == 0;
            }

            @Override
            public long getSize() {
                return file.length();
            }

            @Override
            public byte[] getBytes() throws IOException {
                return Files.readAllBytes(file.toPath());
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(file);
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        };
    }
}