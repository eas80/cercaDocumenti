package com.example.documentstore.security;

import com.example.documentstore.repository.SettingsStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The set of accounts is still the fixed list from {@code
 * documentstore.auth.users}; what that list configures is only each
 * account's <em>initial</em> password. Once a user changes it, the new
 * (bcrypt-hashed) password is persisted in the {@link SettingsStore} and
 * takes precedence from then on - including across restarts, where the
 * configured one would otherwise come back.
 */
@Service
public class UserAccountService implements UserDetailsService {

    private static final String SETTINGS_KEY = "passwords";
    static final int MIN_PASSWORD_LENGTH = 8;

    private final Map<String, String> configuredPasswordHashes;
    private final SettingsStore settingsStore;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public UserAccountService(
            List<ConfiguredUser> resolvedUsers,
            SettingsStore settingsStore,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.configuredPasswordHashes = resolvedUsers.stream()
                .collect(Collectors.toUnmodifiableMap(ConfiguredUser::username, u -> passwordEncoder.encode(u.password())));
        this.settingsStore = settingsStore;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return User.withUsername(username)
                .password(currentPasswordHash(username))
                .roles("USER")
                .build();
    }

    /** Rejects with 403 (not 401, which the frontend treats as "session expired") when the current password is wrong. */
    public synchronized void changePassword(String username, String currentPassword, String newPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, currentPasswordHash(username))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        Map<String, String> changed = loadChangedPasswordHashes();
        changed.put(username, passwordEncoder.encode(newPassword));
        try {
            settingsStore.write(SETTINGS_KEY, objectMapper.writeValueAsBytes(changed));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize changed passwords", e);
        }
    }

    private synchronized String currentPasswordHash(String username) {
        String configured = configuredPasswordHashes.get(username);
        if (configured == null) {
            throw new UsernameNotFoundException(username);
        }
        return loadChangedPasswordHashes().getOrDefault(username, configured);
    }

    private Map<String, String> loadChangedPasswordHashes() {
        return settingsStore.read(SETTINGS_KEY)
                .map(bytes -> {
                    try {
                        return new HashMap<>(objectMapper.readValue(bytes, new TypeReference<Map<String, String>>() {
                        }));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to parse stored passwords", e);
                    }
                })
                .orElseGet(HashMap::new);
    }
}
