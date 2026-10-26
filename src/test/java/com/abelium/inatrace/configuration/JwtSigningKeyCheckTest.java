package com.abelium.inatrace.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSigningKeyCheckTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingKeyIsRejected(String key) {
        assertThatThrownBy(() -> JwtSigningKeyCheck.validate(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not set");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sign", "0123456789abcdef0123456789abcde"})
    void shortKeyIsRejected(String key) {
        assertThatThrownBy(() -> JwtSigningKeyCheck.validate(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void keyOfAtLeast32BytesIsAccepted() {
        assertThatCode(() -> JwtSigningKeyCheck.validate("0123456789abcdef0123456789abcdef")).doesNotThrowAnyException();
    }
}
