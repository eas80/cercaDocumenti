package com.example.documentstore.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void stripsTrailingSlashSoItStillMatchesTheBrowsersOriginHeader() {
        assertThat(WebConfig.parseOrigins("https://cercadocumenti.onrender.com/"))
                .containsExactly("https://cercadocumenti.onrender.com");
    }

    @Test
    void trimsAndSplitsMultipleCommaSeparatedOrigins() {
        assertThat(WebConfig.parseOrigins(" https://a.example.com/ , https://b.example.com "))
                .containsExactly("https://a.example.com", "https://b.example.com");
    }

    @Test
    void blankConfigurationYieldsNoOrigins() {
        assertThat(WebConfig.parseOrigins("")).isEmpty();
    }
}
