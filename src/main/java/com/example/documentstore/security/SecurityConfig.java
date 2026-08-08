package com.example.documentstore.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Stateless JWT-based authentication for every {@code /api/**} endpoint
 * except {@code /api/auth/login}. Users come from a fixed list configured
 * via {@code documentstore.auth.users} (comma-separated {@code user:pass}
 * pairs) - no self-registration, no user database. If left unconfigured, a
 * single random-password admin account is generated and logged at startup
 * (never a silent hardcoded default) - see {@link #buildUserDetailsService}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${documentstore.auth.users:}") String usersProperty,
            PasswordEncoder passwordEncoder) {
        return buildUserDetailsService(usersProperty, passwordEncoder);
    }

    static UserDetailsService buildUserDetailsService(String usersProperty, PasswordEncoder passwordEncoder) {
        List<ConfiguredUser> configuredUsers = parseUsers(usersProperty);

        if (configuredUsers.isEmpty()) {
            String generatedPassword = generateRandomPassword();
            log.warn("AUTH: documentstore.auth.users is empty - generated a single account "
                    + "(username='admin', password='{}') for this process. Set DOCUMENTSTORE_AUTH_USERS "
                    + "(comma-separated username:password pairs) to configure real accounts.", generatedPassword);
            configuredUsers = List.of(new ConfiguredUser("admin", generatedPassword));
        }

        List<UserDetails> userDetails = configuredUsers.stream()
                .map(u -> User.withUsername(u.username())
                        .password(passwordEncoder.encode(u.password()))
                        .roles("USER")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(userDetails);
    }

    static List<ConfiguredUser> parseUsers(String usersProperty) {
        return Arrays.stream(usersProperty.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> {
                    int separator = entry.indexOf(':');
                    if (separator <= 0 || separator == entry.length() - 1) {
                        throw new IllegalArgumentException(
                                "Invalid entry in documentstore.auth.users: '" + entry + "' (expected username:password)");
                    }
                    return new ConfiguredUser(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
                })
                .toList();
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
