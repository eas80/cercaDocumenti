package com.example.documentstore.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated liveness check for Render (and similar platforms) - every
 * other /api/** endpoint requires a valid JWT, so the health check can't use
 * those anymore. See SecurityConfig's permitAll list.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
