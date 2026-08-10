package com.example.documentstore.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final Set<String> configuredUsernames;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, Set<String> configuredUsernames) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.configuredUsernames = configuredUsernames;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String token = jwtService.generateToken(request.username());
        return new LoginResponse(token, request.username());
    }

    /** All configured usernames (protected - not part of the public login endpoint), used to populate a document-sharing picker. */
    @GetMapping("/users")
    public List<String> listUsers() {
        return configuredUsernames.stream().sorted().toList();
    }
}
