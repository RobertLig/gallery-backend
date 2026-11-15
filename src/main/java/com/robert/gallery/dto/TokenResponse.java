package com.robert.gallery.dto;

public record TokenResponse(
        String accessToken,
        long expiresIn,
        UserResponse user
) {
}
