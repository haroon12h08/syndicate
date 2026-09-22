package com.syndicate.evidence;

import com.syndicate.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    /**
     * Stores the bytes under their SHA-256 ({@code sha256/ab/<hash>}). Content-addressed paths are
     * write-once: identical bytes resolve to the same file and an existing file is never rewritten.
     */
    public String store(byte[] content, String sha256) {
        if (content.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256 hex digest");
        }
        String relative = "sha256/" + sha256.substring(0, 2) + "/" + sha256;
        Path target = uploadRoot.resolve(relative).normalize();
        try {
            if (Files.exists(target)) {
                return relative;
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException raced) {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return relative;
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
}
