package com.syndicate.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumUtilTest {

    @Test
    void hashesEmptyInputToKnownVector() {
        assertThat(ChecksumUtil.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hashesKnownNistTestVector() {
        assertThat(ChecksumUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sameInputAlwaysProducesSameHash() {
        byte[] content = "syndicate".getBytes(StandardCharsets.UTF_8);
        assertThat(ChecksumUtil.sha256Hex(content)).isEqualTo(ChecksumUtil.sha256Hex(content));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertThat(ChecksumUtil.sha256Hex("a".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(ChecksumUtil.sha256Hex("b".getBytes(StandardCharsets.UTF_8)));
    }
}
