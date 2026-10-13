package com.example.documentstore.security;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
