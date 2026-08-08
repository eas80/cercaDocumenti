package com.example.documentstore.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void roundTripsAUsernameThroughAGeneratedToken() {
        JwtService jwtService = new JwtService("", 60);

        String token = jwtService.generateToken("alice");

        assertThat(jwtService.extractUsername(token)).contains("alice");
    }

    @Test
    void rejectsATamperedToken() {
        JwtService jwtService = new JwtService("", 60);

        String token = jwtService.generateToken("alice");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThat(jwtService.extractUsername(tampered)).isEmpty();
    }

    @Test
    void tokensFromDifferentSecretsDoNotValidateAcrossInstances() {
        JwtService first = new JwtService("", 60);
        JwtService second = new JwtService("", 60);

        String token = first.generateToken("alice");

        assertThat(second.extractUsername(token)).isEmpty();
    }
}
