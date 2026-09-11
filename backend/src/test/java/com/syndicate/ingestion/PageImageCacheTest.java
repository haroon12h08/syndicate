package com.syndicate.ingestion;

import com.syndicate.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageImageCacheTest {

    @Test
    void savesAndLoadsAPageImage(@TempDir Path tempDir) throws Exception {
        PageImageCache cache = new PageImageCache(tempDir.toString());
        UUID evidenceId = UUID.randomUUID();
        byte[] content = {1, 2, 3, 4};

        assertThat(cache.exists(evidenceId, 1)).isFalse();

        cache.save(evidenceId, 1, content);

        assertThat(cache.exists(evidenceId, 1)).isTrue();
        byte[] loaded = cache.load(evidenceId, 1).getInputStream().readAllBytes();
        assertThat(loaded).isEqualTo(content);
    }

    @Test
    void throwsResourceNotFoundForMissingPage(@TempDir Path tempDir) {
        PageImageCache cache = new PageImageCache(tempDir.toString());

        assertThatThrownBy(() -> cache.load(UUID.randomUUID(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void differentPagesOfSameEvidenceAreIndependent(@TempDir Path tempDir) throws Exception {
        PageImageCache cache = new PageImageCache(tempDir.toString());
        UUID evidenceId = UUID.randomUUID();

        cache.save(evidenceId, 1, new byte[]{1});
        cache.save(evidenceId, 2, new byte[]{2});

        assertThat(cache.load(evidenceId, 1).getInputStream().readAllBytes()).isEqualTo(new byte[]{1});
        assertThat(cache.load(evidenceId, 2).getInputStream().readAllBytes()).isEqualTo(new byte[]{2});
    }
}
