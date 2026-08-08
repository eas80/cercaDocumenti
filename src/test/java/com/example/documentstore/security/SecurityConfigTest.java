package com.example.documentstore.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void parsesMultipleCommaSeparatedUsers() {
        assertThat(SecurityConfig.parseUsers("alice:secret1, bob:secret2 "))
                .containsExactly(new ConfiguredUser("alice", "secret1"), new ConfiguredUser("bob", "secret2"));
    }

    @Test
    void blankConfigurationYieldsNoUsers() {
        assertThat(SecurityConfig.parseUsers("")).isEmpty();
    }

    @Test
    void rejectsEntryWithoutAColon() {
        assertThatThrownBy(() -> SecurityConfig.parseUsers("alice-no-password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEntryWithEmptyPassword() {
        assertThatThrownBy(() -> SecurityConfig.parseUsers("alice:"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
