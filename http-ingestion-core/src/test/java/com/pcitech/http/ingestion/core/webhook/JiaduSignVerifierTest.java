package com.pcitech.http.ingestion.core.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JiaduSignVerifierTest {

    @Test
    void md5Uppercase_platFlagUnderscoreEventId() {
        assertThat(JiaduSignVerifier.compute("ivsp", "UUID00001"))
                .isEqualTo("CB0498DC24B6898EE2248257ECD4A01C");
    }

    @Test
    void verify_acceptsMatchingSignCaseInsensitively() {
        String sign = JiaduSignVerifier.compute("ivsp", "UUID00001");
        assertThat(JiaduSignVerifier.verify("ivsp", "UUID00001", sign.toLowerCase())).isTrue();
    }
}
