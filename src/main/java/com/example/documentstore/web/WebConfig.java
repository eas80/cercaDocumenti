package com.example.documentstore.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Cross-origin access for the frontend, when it's deployed on a different
 * origin than the backend (e.g. two separate Render services). Configure via
 * {@code documentstore.cors.allowed-origins} (comma-separated); left empty,
 * no cross-origin mapping is registered at all.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${documentstore.cors.allowed-origins:}") String allowedOriginsProperty) {
        this.allowedOrigins = parseOrigins(allowedOriginsProperty);
        if (allowedOrigins.isEmpty()) {
            log.info("CORS: documentstore.cors.allowed-origins is empty (raw value: '{}') - "
                    + "no cross-origin mapping registered, /api/** will reject cross-origin requests", allowedOriginsProperty);
            log.info("CORS: raw process env vars matching CORS/ORIGIN: {}", dumpMatchingEnvVars());
            log.info("CORS: ALL env var names visible to this process ({} total): {}",
                    System.getenv().size(),
                    System.getenv().keySet().stream().sorted().collect(Collectors.joining(", ")));
        } else {
            log.info("CORS: allowing origins {} for /api/**", allowedOrigins);
        }
    }

    /**
     * Reads straight from the OS process environment (bypassing Spring's
     * property resolution) so a mismatched/misspelled env var name is
     * visible even when Spring's relaxed binding finds nothing.
     */
    private static String dumpMatchingEnvVars() {
        String dump = System.getenv().entrySet().stream()
                .filter(entry -> entry.getKey().toUpperCase(Locale.ROOT).contains("CORS")
                        || entry.getKey().toUpperCase(Locale.ROOT).contains("ORIGIN"))
                .map(entry -> "[" + entry.getKey() + "]=[" + entry.getValue() + "]")
                .collect(Collectors.joining(", "));
        return dump.isEmpty() ? "<none found>" : dump;
    }

    static List<String> parseOrigins(String allowedOriginsProperty) {
        return Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                // Browsers never send a trailing slash in the Origin header, but it's an
                // easy copy-paste mistake to configure one - strip it so it still matches.
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "PUT", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "X-Document-Id", "X-Document-Name", "X-Document-Last-Modified");
    }
}
