package com.syndicate.ingestion;

import com.syndicate.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class PageImageCache {

    private final Path root;

    public PageImageCache(@Value("${syndicate.storage.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + root, e);
        }
    }

    public void save(UUID evidenceId, int pageNumber, byte[] pngBytes) {
        Path target = root.resolve(fileName(evidenceId, pageNumber));
        try {
            Files.write(target, pngBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to cache page image", e);
        }
    }

    public boolean exists(UUID evidenceId, int pageNumber) {
        return Files.exists(root.resolve(fileName(evidenceId, pageNumber)));
    }

    public Resource load(UUID evidenceId, int pageNumber) {
        Path file = root.resolve(fileName(evidenceId, pageNumber));
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("No cached image for evidence " + evidenceId + " page " + pageNumber);
        }
        try {
            return new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid cached image path", e);
        }
    }

    private String fileName(UUID evidenceId, int pageNumber) {
        return evidenceId + "-page-" + pageNumber + ".png";
    }
}
