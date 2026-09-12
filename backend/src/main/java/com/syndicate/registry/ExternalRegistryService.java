package com.syndicate.registry;

import java.util.Optional;

/**
 * A source of external corporate-registry truth. Implementations are swappable so a real MCA
 * gateway can replace the mock without any caller changing.
 */
public interface ExternalRegistryService {

    /** Identifier for this source, recorded on every snapshot. */
    String source();

    /** Looks the company up by CIN, falling back to PAN when the registry supports it. */
    Optional<RegistryRecord> lookup(String cin, String pan);
}
