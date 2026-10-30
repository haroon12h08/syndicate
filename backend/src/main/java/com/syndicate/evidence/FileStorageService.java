package com.syndicate.evidence;

import com.syndicate.evidence.storage.BlobStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Delegating facade maintaining backward compatibility with existing services
 * while routing all operations to the configured {@link BlobStorageService} driver.
 */
@Service
public class FileStorageService {

    private final BlobStorageService delegate;

    public FileStorageService(BlobStorageService delegate) {
        this.delegate = delegate;
    }

    public String store(byte[] content, String sha256) {
        return delegate.store(content, sha256);
    }

    public byte[] readBytes(String storagePath) {
        return delegate.readBytes(storagePath);
    }

    public Resource load(String storagePath) {
        return delegate.load(storagePath);
    }

    public boolean exists(String storagePath) {
        return delegate.exists(storagePath);
    }

    public String getBackendType() {
        return delegate.getBackendType();
    }
}
