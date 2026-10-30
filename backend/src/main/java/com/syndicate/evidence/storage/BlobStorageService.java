package com.syndicate.evidence.storage;

import org.springframework.core.io.Resource;

/**
 * Storage interface for content-addressed immutable blobs.
 * Adheres to write-once semantics under {@code sha256/ab/<hash>}.
 */
public interface BlobStorageService {

    /**
     * Stores content-addressed bytes and returns the canonical relative path.
     */
    String store(byte[] content, String sha256);

    /**
     * Reads all bytes stored at the given path.
     */
    byte[] readBytes(String storagePath);

    /**
     * Loads the stored blob as a Spring Resource for streaming.
     */
    Resource load(String storagePath);

    /**
     * Checks if the given blob exists in storage.
     */
    boolean exists(String storagePath);

    /**
     * Identifies the active driver type: "FILESYSTEM" or "S3".
     */
    String getBackendType();
}
