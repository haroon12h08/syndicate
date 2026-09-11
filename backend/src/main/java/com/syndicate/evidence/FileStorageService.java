package com.syndicate.evidence;

import com.syndicate.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${syndicate.storage.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadRoot, e);
        }
    }

    public String store(byte[] content, String originalFilename) {
        if (content.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String originalName = Path.of(originalFilename == null ? "file" : originalFilename)
                .getFileName().toString();
        String storedName = UUID.randomUUID() + "-" + originalName;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid file name");
        }
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return storedName;
    }

    public byte[] readBytes(String storagePath) {
        Path file = uploadRoot.resolve(storagePath).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid storage path");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file: " + storagePath, e);
        }
    }

    public Resource load(String storagePath) {
        try {
            Path file = uploadRoot.resolve(storagePath).normalize();
            if (!file.startsWith(uploadRoot)) {
                throw new BadRequestException("Invalid storage path");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BadRequestException("Stored file is missing: " + storagePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BadRequestException("Invalid storage path");
        }
    }

    public void delete(String storagePath) {
        try {
            Path file = uploadRoot.resolve(storagePath).normalize();
            if (file.startsWith(uploadRoot)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // best-effort cleanup for a prototype
        }
    }
}
